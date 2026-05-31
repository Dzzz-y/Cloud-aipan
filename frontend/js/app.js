/**
 * DCloud AiPan - 应用主控模块
 * 应用初始化、面板切换、标签栏、底部面板、个人设置、启动
 * 依赖：common.js, files.js, share.js, recycle.js, ai.js, auth.js
 */
var App = window.App;
(function() {
'use strict';

// ==================== 本地别名 ====================
var STATE = App.STATE;
var $ = App.$;
var $$ = App.$$;
var terminalLog = App.terminalLog;
var formatSize = App.formatSize;
var escHtml = App.escHtml;
var escAttr = App.escAttr;

// ==================== 应用初始化 ====================
async function initApp() {
  try {
    STATE.user = await API.getUserDetail();
    updateStatusBar();
    await App.loadFolderTree();
    await App.loadFileList('0');
    setupPanelSwitching();
    setupBottomPanel();
    App.setupToolbarButtons();
    App.setupFileSearch();
    App.setupAIChat();
    App.setupAIDoc();
    App.setupAIPan();
    App.setupFilePicker();
    App.setupContextMenuHandlers();
    terminalLog('系统就绪', 'success');
  } catch (err) {
    terminalLog('初始化失败: ' + err.message, 'error');
  }
}
App.initApp = initApp;

// ==================== 状态栏 ====================
function updateStatusBar() {
  if (!STATE.user) return;
  $('#status-user').textContent = STATE.user.username + ' (' + (STATE.user.phone || '') + ')';
  if (STATE.user.storageDTO) {
    var used = formatSize(STATE.user.storageDTO.usedSize || 0);
    var total = formatSize(STATE.user.storageDTO.totalSize || 0);
    $('#status-storage').textContent = '存储: ' + used + ' / ' + total;
  }
  $('#status-backend').textContent = '后端: 8080';
  $('#status-ai').textContent = 'AI: 8000';
}

// ==================== 面板切换 ====================
function setupPanelSwitching() {
  $$('.activity-item').forEach(function(item) {
    item.onclick = function() {
      var panel = item.dataset.panel;
      switchPanel(panel);
    };
  });
  // 初始面板
  switchPanel('files');
}

function switchPanel(panel) {
  STATE.currentPanel = panel;
  $$('.activity-item').forEach(function(i) { i.classList.toggle('active', i.dataset.panel === panel); });
  $$('.sidebar-panel').forEach(function(p) { p.classList.toggle('active', p.id === 'panel-' + panel); });

  // 更新主内容区标签
  var tabNames = {
    'files': '文件管理', 'share': '分享管理', 'recycle': '回收站',
    'ai-chat': 'AI 对话', 'ai-doc': 'AI 文档总结', 'ai-pan': 'AI 网盘查询',
    'settings': '个人设置'
  };
  setActiveTab(panel, tabNames[panel] || panel);

  // 加载对应数据
  switch (panel) {
    case 'files': App.loadFileList(STATE.currentParentId); break;
    case 'share': App.loadShareList(); break;
    case 'recycle': App.loadRecycleList(); break;
    case 'ai-chat': renderAIChatPanel(); break;
    case 'ai-doc': renderAIDocPanel(); break;
    case 'ai-pan': renderAIPanPanel(); break;
    case 'settings': loadUserProfile(); break;
  }
}

// ==================== 标签栏 ====================
function setActiveTab(id, name) {
  var tabsBar = $('#tabs-bar');
  // 避免重复
  var existing = $('.tab-item[data-tab="' + id + '"]');
  if (existing) {
    $$('.tab-item').forEach(function(t) { t.classList.remove('active'); });
    existing.classList.add('active');
    return;
  }
  $$('.tab-item').forEach(function(t) { t.classList.remove('active'); });
  var tab = document.createElement('div');
  tab.className = 'tab-item active';
  tab.dataset.tab = id;
  tab.innerHTML = '<span>' + name + '</span><span class="tab-close" data-close="' + id + '">✕</span>';
  tab.onclick = function(e) {
    if (e.target.classList.contains('tab-close')) return;
    switchPanel(id);
  };
  tab.querySelector('.tab-close').onclick = function(e) {
    e.stopPropagation();
    tab.remove();
    if (tab.classList.contains('active')) {
      var first = $('.tab-item');
      if (first) first.click();
    }
  };
  tabsBar.appendChild(tab);
  STATE.currentTab = id;
}

// ==================== 底部面板切换 ====================
function setupBottomPanel() {
  $$('.bottom-tab').forEach(function(tab) {
    tab.onclick = function() {
      $$('.bottom-tab').forEach(function(t) { t.classList.remove('active'); });
      tab.classList.add('active');
      $$('.bottom-panel-content').forEach(function(p) { p.classList.remove('active'); });
      var panel = $('#bottom-' + tab.dataset.bottom);
      if (panel) panel.classList.add('active');
    };
  });
}

// ==================== 用户设置 ====================
function loadUserProfile() {
  if (!STATE.user) return;
  var container = $('#content-area');
  container.innerHTML =
    '<div style="padding:24px;max-width:480px">' +
      '<h3>个人设置</h3>' +
      '<div style="margin:16px 0">' +
        '<div class="form-group"><label>用户名</label><input type="text" value="' + escAttr(STATE.user.username) + '" readonly></div>' +
        '<div class="form-group"><label>手机号</label><input type="text" value="' + escAttr(STATE.user.phone || '') + '" readonly></div>' +
        '<div class="form-group"><label>角色</label><input type="text" value="' + (STATE.user.role || 'COMMON') + '" readonly></div>' +
        '<div class="form-group"><label>头像</label>' +
          (STATE.user.avatarUrl ? '<img src="' + escAttr(STATE.user.avatarUrl) + '" style="width:64px;height:64px;border-radius:50%;object-fit:cover">' : '<p style="color:var(--text-muted)">未设置头像</p>') +
        '</div>' +
        '<div class="form-group"><label>存储空间</label>' +
          '<div class="progress-bar"><div class="progress-fill" style="width:' + (STATE.user.storageDTO ? (STATE.user.storageDTO.usedSize / STATE.user.storageDTO.totalSize * 100).toFixed(1) : 0) + '%"></div></div>' +
          '<p style="font-size:12px;margin-top:4px;color:var(--text-secondary)">' + formatSize(STATE.user.storageDTO ? STATE.user.storageDTO.usedSize || 0 : 0) + ' / ' + formatSize(STATE.user.storageDTO ? STATE.user.storageDTO.totalSize || 0 : 0) + '</p>' +
        '</div>' +
      '</div>' +
      '<button class="btn-danger" onclick="app.logout()">退出登录</button>' +
    '</div>';
}

// ==================== AI 面板主内容区渲染 ====================

// AI 对话面板
function renderAIChatPanel() {
  var container = $('#content-area');
  container.innerHTML =
    '<div class="ai-chat-main">' +
      '<div class="chat-messages" id="chat-msgs-main"></div>' +
      '<div class="chat-input-area">' +
        '<textarea id="chat-input-main" rows="2" placeholder="输入消息，Enter 发送，Shift+Enter 换行..."></textarea>' +
        '<button class="btn-primary" id="btn-chat-send-main">发送</button>' +
      '</div>' +
    '</div>';

  var input = $('#chat-input-main');
  var sendBtn = $('#btn-chat-send-main');
  sendBtn.onclick = sendContentChat;
  input.onkeydown = function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendContentChat();
    }
  };
  // 渲染历史消息
  (STATE.chatMessages || []).forEach(function(m) {
    appendContentChatMsg(m.role, m.content);
  });
}

function sendContentChat() {
  var input = $('#chat-input-main');
  var message = input.value.trim();
  if (!message || STATE.isChatStreaming) return;
  input.value = '';
  STATE.isChatStreaming = true;
  $('#btn-chat-send-main').disabled = true;

  appendContentChatMsg('user', message);
  if (!STATE.chatMessages) STATE.chatMessages = [];
  STATE.chatMessages.push({ role: 'user', content: message });

  var assistantMsg = appendContentChatMsg('assistant', '');
  var fullContent = '';

  API.chatStream(
    message,
    function(chunk) {
      fullContent += chunk;
      assistantMsg.querySelector('.msg-content').textContent = fullContent;
      var msgsEl = $('#chat-msgs-main');
      if (msgsEl) msgsEl.scrollTop = msgsEl.scrollHeight;
    },
    function() {
      STATE.isChatStreaming = false;
      $('#btn-chat-send-main').disabled = false;
      STATE.chatMessages.push({ role: 'assistant', content: fullContent });
    },
    function(err) {
      assistantMsg.querySelector('.msg-content').textContent = '[错误] ' + err;
      STATE.isChatStreaming = false;
      $('#btn-chat-send-main').disabled = false;
    }
  );
}

function appendContentChatMsg(role, content) {
  var container = $('#chat-msgs-main');
  var div = document.createElement('div');
  div.className = 'chat-msg ' + role;
  div.innerHTML = '<div class="msg-role">' + (role === 'user' ? '🧑 你' : '🤖 AI助手') + '</div>' +
    '<div class="msg-content">' + escHtml(content) + '</div>';
  container.appendChild(div);
  container.scrollTop = container.scrollHeight;
  return div;
}

// AI 文档总结面板
function renderAIDocPanel() {
  var container = $('#content-area');
  container.innerHTML =
    '<div class="ai-doc-main">' +
      '<div class="doc-input-area" style="padding:16px;gap:12px;flex-wrap:wrap;border-bottom:1px solid var(--border-subtle)">' +
        '<input type="text" id="doc-url-main" placeholder="输入文档URL..." style="flex:1;min-width:200px;">' +
        '<button type="button" class="btn-sm" id="btn-pick-file-main" style="white-space:nowrap;">📁 从网盘选择</button>' +
        '<select id="doc-summary-type-main">' +
          '<option value="brief">简要</option>' +
          '<option value="detailed">详细</option>' +
          '<option value="keypoints">要点</option>' +
        '</select>' +
        '<select id="doc-language-main">' +
          '<option value="zh">中文</option>' +
          '<option value="en">英文</option>' +
        '</select>' +
        '<button class="btn-primary" id="btn-doc-process-main">开始总结</button>' +
      '</div>' +
      '<div class="doc-result" id="doc-result-main" style="flex:1;overflow-y:auto;margin:16px;min-height:200px;"></div>' +
    '</div>';

  $('#btn-doc-process-main').onclick = startDocProcessMain;
  App.bindDocPickFileMain();
}

async function startDocProcessMain() {
  var url = ($('#doc-url-main').value || '').trim();
  var summaryType = $('#doc-summary-type-main').value || 'brief';
  var language = $('#doc-language-main').value || 'zh';
  if (!url) { App.toast('请输入文档URL', 'error'); return; }

  var resultDiv = $('#doc-result-main');
  resultDiv.innerHTML = '<div class="streaming"><span class="spinner"></span> 正在分析文档...</div>';
  $('#btn-doc-process-main').disabled = true;

  var fullContent = '';
  await API.documentStream(
    url, summaryType, language, null, null,
    function(chunk) {
      fullContent += chunk;
      resultDiv.innerHTML = '<div class="streaming">' + escHtml(fullContent) + '</div>';
    },
    function() {
      resultDiv.innerHTML = '<div>' + escHtml(fullContent) + '</div>';
      $('#btn-doc-process-main').disabled = false;
      terminalLog('文档总结完成', 'success');
    },
    function(err) {
      resultDiv.innerHTML = '<div style="color:var(--error)">错误: ' + escHtml(err) + '</div>';
      $('#btn-doc-process-main').disabled = false;
    }
  );
}

// AI 网盘查询面板
function renderAIPanPanel() {
  var container = $('#content-area');
  container.innerHTML =
    '<div class="ai-pan-main" style="display:flex;flex-direction:column;height:100%;padding:16px;gap:12px;">' +
      '<div class="pan-input-area" style="display:flex;gap:8px;">' +
        '<textarea id="pan-query-main" rows="2" placeholder="自然语言查询，例如：查看我的所有图片文件、统计存储空间使用情况..." style="flex:1;min-width:200px;resize:vertical;"></textarea>' +
        '<button class="btn-primary" id="btn-pan-query-main">查询</button>' +
      '</div>' +
      '<div class="pan-result" id="pan-result-main" style="flex:1;overflow-y:auto;"></div>' +
    '</div>';

  $('#btn-pan-query-main').onclick = startPanQueryMain;
}

async function startPanQueryMain() {
  var query = ($('#pan-query-main').value || '').trim();
  if (!query) { App.toast('请输入查询内容', 'error'); return; }

  var resultDiv = $('#pan-result-main');
  resultDiv.innerHTML = '<span class="spinner"></span> 查询中...';
  $('#btn-pan-query-main').disabled = true;

  try {
    var result = await API.panQuery(query);
    resultDiv.innerHTML = renderPanResultMain(result);
    var rdata = result.data || result;
    if (result.type === 'file_list' && Array.isArray(rdata) && rdata.length > 0) {
      var galleryContainer = resultDiv.querySelector('#pan-result-gallery-main');
      if (galleryContainer) App.renderPanFileCards(galleryContainer, rdata);
    }
    terminalLog('网盘查询完成', 'success');
  } catch (err) {
    resultDiv.innerHTML = '<div style="color:var(--error)">查询失败: ' + escHtml(err.message) + '</div>';
  }
  $('#btn-pan-query-main').disabled = false;
}

function renderPanResultMain(result) {
  if (!result) return '<p>无结果</p>';
  var data = result.data || result;
  var type = result.type || (data ? data.type : null) || 'text';

  // 文件列表 → 占位容器，由 renderPanFileCards 异步填充
  if (type === 'file_list' && Array.isArray(data)) {
    return '<div id="pan-result-gallery-main"><span class="spinner"></span> 加载文件预览...</div>';
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

  if (data.content) {
    return '<div style="padding:12px;white-space:pre-wrap">' + escHtml(data.content) + '</div>';
  }

  return '<pre style="padding:12px;font-size:12px">' + escHtml(JSON.stringify(result, null, 2)) + '</pre>';
}

window.app.logout = function() {
  API.clearToken();
  STATE.user = null;
  STATE.selectedFiles.clear();
  STATE.currentFiles = [];
  STATE.chatMessages = [];
  $('#app').style.display = 'none';
  $('#auth-overlay').style.display = 'flex';
  $('#chat-messages').innerHTML = '';
  terminalLog('已退出登录', 'warn');
};

// ==================== 启动 ====================
document.addEventListener('DOMContentLoaded', function() {
  App.initAuth();
});

})();
