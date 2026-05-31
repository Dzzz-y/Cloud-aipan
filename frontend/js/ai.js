/**
 * DCloud AiPan - AI 功能模块
 * AI 对话、AI 文档总结、AI 网盘查询
 */
var App = window.App;
(function() {
'use strict';

// ==================== 本地别名 ====================
var STATE = App.STATE;
var $ = App.$;
var $$ = App.$$;
var toast = App.toast;
var terminalLog = App.terminalLog;
var formatSize = App.formatSize;
var escHtml = App.escHtml;
var escAttr = App.escAttr;

// ==================== AI 对话 ====================
function setupAIChat() {
  var chatInput = $('#chat-input');
  var sendBtn = $('#btn-chat-send');

  sendBtn.onclick = sendChatMessage;
  chatInput.onkeydown = function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendChatMessage();
    }
  };
}
App.setupAIChat = setupAIChat;

async function sendChatMessage() {
  var input = $('#chat-input');
  var message = input.value.trim();
  if (!message || STATE.isChatStreaming) return;
  input.value = '';
  STATE.isChatStreaming = true;
  $('#btn-chat-send').disabled = true;

  appendChatMessage('user', message);
  var assistantMsg = appendChatMessage('assistant', '');

  await API.chatStream(
    message,
    function(chunk) { assistantMsg.querySelector('.msg-content').textContent += chunk; scrollChatBottom(); },
    function() { STATE.isChatStreaming = false; $('#btn-chat-send').disabled = false; scrollChatBottom(); },
    function(err) {
      assistantMsg.querySelector('.msg-content').textContent = '[错误] ' + err;
      STATE.isChatStreaming = false;
      $('#btn-chat-send').disabled = false;
      scrollChatBottom();
    }
  );
}

function appendChatMessage(role, content) {
  var container = $('#chat-messages');
  var div = document.createElement('div');
  div.className = 'chat-msg ' + role;
  div.innerHTML = '<div class="msg-role">' + (role === 'user' ? '🧑 你' : '🤖 AI助手') + '</div>' +
    '<div class="msg-content">' + escHtml(content) + '</div>';
  container.appendChild(div);
  return div;
}

function scrollChatBottom() {
  var container = $('#chat-messages');
  container.scrollTop = container.scrollHeight;
}

// ==================== AI 文档总结 ====================
function setupAIDoc() {
  var btnDocProcess = $('#btn-doc-process');
  if (btnDocProcess) btnDocProcess.onclick = startDocProcess;
  var btnDocProcessSide = $('#btn-doc-process-side');
  if (btnDocProcessSide) btnDocProcessSide.onclick = startDocProcess;
}
App.setupAIDoc = setupAIDoc;

async function startDocProcess() {
  var urlEl = $('#doc-url');
  var urlSideEl = $('#doc-url-side');
  var url = ((urlEl ? urlEl.value : '') || (urlSideEl ? urlSideEl.value : '') || '').trim();
  var summaryTypeEl = $('#doc-summary-type');
  var summaryTypeSideEl = $('#doc-summary-type-side');
  var summaryType = (summaryTypeEl ? summaryTypeEl.value : '') || (summaryTypeSideEl ? summaryTypeSideEl.value : '') || 'brief';
  var languageEl = $('#doc-language');
  var language = languageEl ? languageEl.value : 'zh';

  if (!url) { toast('请输入文档URL', 'error'); return; }

  var resultDiv = $('#doc-result');
  resultDiv.innerHTML = '<div class="streaming"><span class="spinner"></span> 正在分析文档...</div>';
  var btnProcess = $('#btn-doc-process');
  if (btnProcess) btnProcess.disabled = true;
  var btnProcessSide = $('#btn-doc-process-side');
  if (btnProcessSide) btnProcessSide.disabled = true;

  var fullContent = '';
  await API.documentStream(
    url, summaryType, language, null, null,
    function(chunk) {
      fullContent += chunk;
      resultDiv.innerHTML = '<div class="streaming">' + escHtml(fullContent) + '</div>';
    },
    function() {
      resultDiv.innerHTML = '<div>' + escHtml(fullContent) + '</div>';
      var bp = $('#btn-doc-process');
      if (bp) bp.disabled = false;
      var bps = $('#btn-doc-process-side');
      if (bps) bps.disabled = false;
      terminalLog('文档总结完成', 'success');
    },
    function(err) {
      resultDiv.innerHTML = '<div style="color:var(--error)">错误: ' + escHtml(err) + '</div>';
      var bp = $('#btn-doc-process');
      if (bp) bp.disabled = false;
      var bps = $('#btn-doc-process-side');
      if (bps) bps.disabled = false;
    }
  );
}

// ==================== 文档文件选择器 ====================
var filePickerState = null;  // { targetInputId, currentParentId, breadcrumbPath }

function setupFilePicker() {
  var btnPick = $('#btn-pick-file');
  if (btnPick) btnPick.onclick = function() { showFilePickerModal('doc-url'); };
  var btnPickSide = $('#btn-pick-file-side');
  if (btnPickSide) btnPickSide.onclick = function() { showFilePickerModal('doc-url-side'); };
}
App.setupFilePicker = setupFilePicker;

/** 绑定主内容区域的按钮（由 app.js renderAIDocPanel 动态创建后调用） */
App.bindDocPickFileMain = function() {
  var btn = $('#btn-pick-file-main');
  if (btn) btn.onclick = function() { showFilePickerModal('doc-url-main'); };
};

function showFilePickerModal(targetInputId) {
  filePickerState = {
    targetInputId: targetInputId,
    currentParentId: '0',
    breadcrumbPath: [{ id: '0', name: '根目录' }]
  };
  renderFilePickerModal();
  loadFilePickerList('0');
}

function renderFilePickerModal() {
  var overlay = $('#modal-overlay');
  var content = $('#modal-content');
  content.innerHTML =
    '<h3>📁 从网盘选择文件</h3>' +
    '<div class="modal-body">' +
      '<div class="file-picker-breadcrumb" id="picker-breadcrumb"></div>' +
      '<div class="file-picker-list" id="picker-file-list">' +
        '<div style="text-align:center;padding:24px"><span class="spinner"></span> 加载中...</div>' +
      '</div>' +
    '</div>' +
    '<div class="modal-actions">' +
      '<button class="btn-cancel" id="modal-cancel">取消</button>' +
    '</div>';
  overlay.style.display = 'flex';

  $('#modal-cancel').onclick = function() { overlay.style.display = 'none'; filePickerState = null; };
  overlay.onclick = function(e) { if (e.target === overlay) { overlay.style.display = 'none'; filePickerState = null; } };

  updatePickerBreadcrumb();
}

function updatePickerBreadcrumb() {
  var bc = $('#picker-breadcrumb');
  if (!bc || !filePickerState) return;
  var path = filePickerState.breadcrumbPath;
  bc.innerHTML = path.map(function(p, i) {
    var isLast = i === path.length - 1;
    return '<span class="breadcrumb-item" data-id="' + escHtml(String(p.id)) + '">' + escHtml(p.name) + '</span>' +
      (isLast ? '' : '<span class="breadcrumb-separator">›</span>');
  }).join('');
  $$('.breadcrumb-item', bc).forEach(function(el) {
    el.onclick = function() {
      var id = String(el.dataset.id);
      var idx = filePickerState.breadcrumbPath.findIndex(function(p) { return String(p.id) === id; });
      if (idx >= 0) {
        filePickerState.breadcrumbPath = filePickerState.breadcrumbPath.slice(0, idx + 1);
        filePickerState.currentParentId = id;
        loadFilePickerList(id);
        updatePickerBreadcrumb();
      }
    };
  });
}

async function loadFilePickerList(parentId) {
  var listEl = $('#picker-file-list');
  if (!listEl) return;
  listEl.innerHTML = '<div style="text-align:center;padding:24px"><span class="spinner"></span> 加载中...</div>';

  try {
    var files = await API.getFileList(parentId);
    filePickerState.currentParentId = String(parentId);
    renderFilePickerList(files);
  } catch (err) {
    listEl.innerHTML = '<div style="text-align:center;padding:24px;color:var(--error)">加载失败: ' + escHtml(err.message) + '</div>';
  }
}

function renderFilePickerList(files) {
  var listEl = $('#picker-file-list');
  if (!listEl) return;

  if (!files || files.length === 0) {
    listEl.innerHTML = '<div class="empty-state" style="padding:32px"><p>此文件夹为空</p></div>';
    return;
  }

  // 文件夹在前，文件在后
  var dirs = files.filter(function(f) { return f.isDir === 1; });
  var fileItems = files.filter(function(f) { return f.isDir === 0; });
  var sorted = dirs.concat(fileItems);

  listEl.innerHTML =
    '<table class="file-table">' +
      '<thead><tr>' +
        '<th class="col-name">名称</th>' +
        '<th class="col-type">类型</th>' +
        '<th class="col-size">大小</th>' +
        '<th class="col-date">修改时间</th>' +
      '</tr></thead>' +
      '<tbody>' + sorted.map(function(f) { return renderFilePickerRow(f); }).join('') + '</tbody>' +
    '</table>';

  // 绑定行点击事件
  $$('.file-picker-row', listEl).forEach(function(row) {
    var fileId = String(row.dataset.fileId);
    var file = sorted.find(function(f) { return String(f.id) === fileId; });

    row.onclick = async function() {
      if (file && file.isDir === 1) {
        // 进入文件夹
        filePickerState.currentParentId = fileId;
        filePickerState.breadcrumbPath.push({ id: fileId, name: file.fileName });
        updatePickerBreadcrumb();
        await loadFilePickerList(fileId);
      } else {
        // 选中文件：获取下载URL并填入输入框
        await pickFileAndSetUrl(file);
      }
    };
  });
}

function renderFilePickerRow(f) {
  var isDir = f.isDir === 1;
  var icon = isDir ? '📁' : (CONFIG.FILE_ICONS[f.fileType] || '📄');
  var typeName = isDir ? '文件夹' : (CONFIG.FILE_TYPE_CN[f.fileType] || f.fileType || '未知');
  var sizeStr = isDir ? '-' : formatSize(f.fileSize || 0);
  var dateStr = f.gmtModified ? new Date(f.gmtModified).toLocaleString('zh-CN') : '-';
  var rowClass = isDir ? 'file-picker-row file-picker-dir' : 'file-picker-row';
  return '<tr class="' + rowClass + '" data-file-id="' + escAttr(String(f.id)) + '" style="cursor:pointer">' +
    '<td><span class="file-icon">' + icon + '</span> ' + escHtml(f.fileName) + '</td>' +
    '<td>' + typeName + '</td>' +
    '<td>' + sizeStr + '</td>' +
    '<td>' + dateStr + '</td>' +
  '</tr>';
}

async function pickFileAndSetUrl(file) {
  var listEl = $('#picker-file-list');
  if (listEl) listEl.innerHTML = '<div style="text-align:center;padding:24px"><span class="spinner"></span> 正在获取文件链接...</div>';

  try {
    // 直接用 file.id（API 返回的原始值），避免 Number() 精度丢失
    var rawId = file.id;
    console.log('[filePicker] 请求下载链接, fileId=', rawId, 'type=', typeof rawId, 'fileName=', file.fileName);
    var urls = await API.getDownloadUrls([rawId]);
    console.log('[filePicker] API 返回 raw=', JSON.stringify(urls));

    var downloadUrl = '';
    // 尝试多种返回格式
    if (Array.isArray(urls) && urls.length > 0) {
      var item = urls[0];
      downloadUrl = typeof item === 'string' ? item : (item.downloadUrl || item.url || '');
    } else if (urls && typeof urls === 'object' && !Array.isArray(urls)) {
      // 可能返回的是单个对象
      downloadUrl = urls.downloadUrl || urls.url || '';
    }

    if (!downloadUrl) {
      console.log('[filePicker] 未能解析出 downloadUrl, urls=', urls);
      toast('无法获取文件链接', 'error');
      loadFilePickerList(filePickerState.currentParentId);
      return;
    }

    console.log('[filePicker] 解析出 downloadUrl=', downloadUrl);

    // 填入目标输入框
    var targetInput = $('#' + filePickerState.targetInputId);
    if (targetInput) {
      targetInput.value = downloadUrl;
      syncDocUrlInputs(downloadUrl);
    }

    // 关闭模态框
    $('#modal-overlay').style.display = 'none';
    filePickerState = null;
    toast('已选择文件: ' + (file.fileName || ''), 'success');
  } catch (err) {
    console.error('[filePicker] 获取文件链接异常:', err);
    toast('获取文件链接失败: ' + err.message, 'error');
    loadFilePickerList(filePickerState.currentParentId);
  }
}

/** 同步 URL 到三个输入框（底部面板、侧边栏、主内容区） */
function syncDocUrlInputs(url) {
  var inputs = ['doc-url', 'doc-url-side', 'doc-url-main'];
  inputs.forEach(function(id) {
    var el = $('#' + id);
    if (el) el.value = url;
  });
}

// ==================== AI 网盘查询 ====================
function setupAIPan() {
  var btnQuery = $('#btn-pan-query');
  if (btnQuery) btnQuery.onclick = startPanQuery;
  var btnQuerySide = $('#btn-pan-query-side');
  if (btnQuerySide) btnQuerySide.onclick = startPanQuery;
}
App.setupAIPan = setupAIPan;

async function startPanQuery() {
  var queryEl = $('#pan-query');
  var querySideEl = $('#pan-query-side');
  var query = ((queryEl ? queryEl.value : '') || (querySideEl ? querySideEl.value : '') || '').trim();
  if (!query) { toast('请输入查询内容', 'error'); return; }

  var resultDiv = $('#pan-result');
  resultDiv.innerHTML = '<span class="spinner"></span> 查询中...';
  var btnQuery = $('#btn-pan-query');
  if (btnQuery) btnQuery.disabled = true;
  var btnQuerySide = $('#btn-pan-query-side');
  if (btnQuerySide) btnQuerySide.disabled = true;

  try {
    var result = await API.panQuery(query);
    resultDiv.innerHTML = renderPanResult(result);
    // 异步加载文件卡片（图片缩略图 / 文件表格）
    var rdata = result.data || result;
    if (result.type === 'file_list' && Array.isArray(rdata) && rdata.length > 0) {
      var galleryContainer = resultDiv.querySelector('#pan-result-gallery');
      if (galleryContainer) App.renderPanFileCards(galleryContainer, rdata);
    }
    terminalLog('网盘查询完成', 'success');
  } catch (err) {
    resultDiv.innerHTML = '<div style="color:var(--error)">查询失败: ' + escHtml(err.message) + '</div>';
  }
  var bq = $('#btn-pan-query');
  if (bq) bq.disabled = false;
  var bqs = $('#btn-pan-query-side');
  if (bqs) bqs.disabled = false;
}

function renderPanResult(result) {
  if (!result) return '<p>无结果</p>';
  var data = result.data || result;
  var type = result.type || (data ? data.type : null) || 'text';

  // 文件列表 → 占位容器，由 renderPanFileCards 异步加载图片预览
  if (type === 'file_list' && Array.isArray(data)) {
    return '<div id="pan-result-gallery"><span class="spinner"></span> 加载文件预览...</div>';
  }

  if (type === 'storage_info' && data.data) {
    var d = data.data;
    return '<div style="padding:12px">' +
      '<p>已使用: <strong>' + formatSize(d.use_size || 0) + '</strong></p>' +
      '<p>总容量: <strong>' + formatSize(d.total_size || 0) + '</strong></p>' +
      '<p>使用率: <strong>' + (d.used_percentage || 0).toFixed(1) + '%</strong></p>' +
      '<div class="progress-bar" style="margin-top:8px"><div class="progress-fill" style="width:' + Math.min(d.used_percentage || 0, 100) + '%"></div></div>' +
    '</div>';
  }

  if (type === 'file_statistics' && data.data) {
    var dd = data.data;
    var typesHtml = '';
    if (dd.file_types) {
      var entries = [];
      for (var k in dd.file_types) {
        if (dd.file_types.hasOwnProperty(k)) {
          entries.push('<span class="tag tag-info">' + k + ': ' + dd.file_types[k] + '</span>');
        }
      }
      typesHtml = '<p>文件类型: ' + entries.join(' ') + '</p>';
    }
    return '<div style="padding:12px">' +
      '<p>总文件数: <strong>' + (dd.total_files || 0) + '</strong></p>' +
      '<p>总大小: <strong>' + formatSize(dd.total_size || 0) + '</strong></p>' +
      typesHtml +
    '</div>';
  }

  // 文本内容
  if (data.content) {
    return '<div style="padding:12px;white-space:pre-wrap">' + escHtml(data.content) + '</div>';
  }

  return '<pre style="padding:12px;font-size:12px">' + escHtml(JSON.stringify(result, null, 2)) + '</pre>';
}

})();
