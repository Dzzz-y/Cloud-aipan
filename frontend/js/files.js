/**
 * DCloud AiPan - 文件管理模块
 * 文件夹树、文件列表、面包屑、工具栏、文件操作（上传/下载/删除/复制/移动/重命名）
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
var showModal = App.showModal;
var showContextMenu = App.showContextMenu;
var formatSize = App.formatSize;
var escHtml = App.escHtml;
var escAttr = App.escAttr;

// ==================== 面包屑 ====================
function updateBreadcrumb(path) {
  STATE.breadcrumbPath = path;
  var bc = $('#breadcrumb');
  bc.innerHTML = path.map(function(p, i) {
    var isLast = i === path.length - 1;
    // 强制输出为安全的字符串形式到 DOM data 属性上
    return '<span class="breadcrumb-item" data-id="' + String(p.id) + '">' + escHtml(p.name) + '</span>' +
      (isLast ? '' : '<span class="breadcrumb-separator">›</span>');
  }).join('');
  $$('.breadcrumb-item', bc).forEach(function(el) {
    el.onclick = function() {
      // 彻底移除任意 parseInt，严防死守
      var id = String(el.dataset.id);
      STATE.currentParentId = id;
      navigateToFolder(id);
    };
  });
}

function navigateToFolder(parentId) {
  STATE.currentParentId = String(parentId);
  // 更新面包屑
  var idx = STATE.breadcrumbPath.findIndex(function(p) { return String(p.id) === String(parentId); });
  if (idx >= 0) {
    updateBreadcrumb(STATE.breadcrumbPath.slice(0, idx + 1));
  }
  loadFileList(parentId);
}

// ==================== 文件夹树 ====================
async function loadFolderTree() {
  try {
    STATE.folderTree = await API.getFolderTree();
    renderFolderTree();
  } catch (err) {
    terminalLog('加载文件夹树失败: ' + err.message, 'error');
  }
}
App.loadFolderTree = loadFolderTree;

function renderFolderTree() {
  var container = $('#folder-tree');
  container.innerHTML = '';
  // 根目录
  var rootItem = createFolderTreeItem({ id: '0', parentId: '0', label: '全部文件夹', children: STATE.folderTree });
  rootItem.classList.add('active');
  container.appendChild(rootItem);

  // 展开根目录子节点
  var rootChildren = document.createElement('div');
  rootChildren.className = 'folder-tree-children';
  STATE.folderTree.forEach(function(node) {
    rootChildren.appendChild(buildFolderTreeNode(node));
  });
  container.appendChild(rootChildren);
}

function buildFolderTreeNode(node) {
  var wrapper = document.createElement('div');
  var item = createFolderTreeItem(node);
  wrapper.appendChild(item);

  if (node.children && node.children.length > 0) {
    var childrenContainer = document.createElement('div');
    childrenContainer.className = 'folder-tree-children';
    childrenContainer.style.display = 'none';
    node.children.forEach(function(child) {
      childrenContainer.appendChild(buildFolderTreeNode(child));
    });
    wrapper.appendChild(childrenContainer);
  }
  return wrapper;
}

function createFolderTreeItem(node) {
  var item = document.createElement('div');
  item.className = 'folder-tree-item';
  item.dataset.folderId = String(node.id);
  item.innerHTML = '<span class="folder-icon">' + (node.children && node.children.length > 0 ? '📂' : '📁') + '</span>' +
    '<span class="folder-name">' + escHtml(node.label) + '</span>';
  item.onclick = function(e) {
    e.stopPropagation();
    $$('.folder-tree-item').forEach(function(i) { i.classList.remove('active'); });
    item.classList.add('active');
    STATE.currentParentId = String(node.id);
    navigateToFolder(node.id);
    // 折叠/展开
    var wrapper = item.parentElement;
    var children = wrapper.querySelector('.folder-tree-children');
    if (children) {
      var icon = item.querySelector('.folder-icon');
      if (children.style.display === 'none') {
        children.style.display = '';
        icon.textContent = '📂';
      } else {
        children.style.display = 'none';
        icon.textContent = '📁';
      }
    }
  };

  // 右键菜单
  item.oncontextmenu = function(e) {
    e.preventDefault();
    App.contextMenuTarget = { type: 'folder', id: String(node.id), name: node.label };
    showContextMenu(e.clientX, e.clientY, [
      { icon: '📁+', label: '新建子文件夹', action: 'new-folder', handler: function() { showNewFolderModal(String(node.id)); } },
      { icon: '📤', label: '上传文件到此', action: 'upload-here', handler: function() { triggerFileUpload(String(node.id)); } },
      { icon: '📋', label: '复制到此', action: 'copy-here', handler: function() { batchCopyTo(String(node.id)); } },
      { icon: '📦', label: '移动到此', action: 'move-here', handler: function() { batchMoveTo(String(node.id)); } },
    ]);
  };

  // 拖放支持
  item.ondragover = function(e) { e.preventDefault(); item.classList.add('dragover'); };
  item.ondragleave = function() { item.classList.remove('dragover'); };
  item.ondrop = function(e) {
    e.preventDefault();
    item.classList.remove('dragover');
    if (STATE.selectedFiles.size > 0) {
      batchMoveTo(String(node.id));
    }
  };

  return item;
}

// ==================== 文件列表 ====================
async function loadFileList(parentId) {
  try {
    STATE.currentFiles = await API.getFileList(parentId);
    STATE.selectedFiles.clear();
    renderFileList();
    updateToolbar();
    // 更新面包屑
    if (String(parentId) === '0') {
      updateBreadcrumb([{ id: '0', name: '根目录' }]);
    }
    terminalLog('加载文件列表: ' + STATE.currentFiles.length + ' 个项目', 'info');
  } catch (err) {
    toast('加载文件列表失败: ' + err.message, 'error');
  }
}
App.loadFileList = loadFileList;

function renderFileList() {
  var area = $('#content-area');
  if (STATE.currentFiles.length === 0) {
    area.innerHTML = '<div class="empty-state">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>' +
      '<p>此文件夹为空</p>' +
      '<p style="font-size:12px;margin-top:4px">上传文件或创建文件夹开始使用</p>' +
    '</div>';
    return;
  }

  var dirs = STATE.currentFiles.filter(function(f) { return f.isDir === 1; });
  var files = STATE.currentFiles.filter(function(f) { return f.isDir === 0; });
  var sorted = dirs.concat(files);

  area.innerHTML =
    '<table class="file-table">' +
      '<thead><tr>' +
        '<th class="col-name"><input type="checkbox" id="select-all"></th>' +
        '<th class="col-name">名称</th>' +
        '<th class="col-type">类型</th>' +
        '<th class="col-size">大小</th>' +
        '<th class="col-date">修改时间</th>' +
        '<th class="col-actions">操作</th>' +
      '</tr></thead>' +
      '<tbody>' + sorted.map(function(f) { return renderFileRow(f); }).join('') + '</tbody>' +
    '</table>';

  // 全选
  $('#select-all').onchange = function(e) {
    sorted.forEach(function(f) {
      if (e.target.checked) STATE.selectedFiles.add(String(f.id));
      else STATE.selectedFiles.delete(String(f.id));
    });
    $$('.file-select').forEach(function(cb) { cb.checked = e.target.checked; });
    updateToolbar();
  };

  // 每行的点击和右键
  $$('.file-item').forEach(function(row) {
    var fileId = String(row.dataset.fileId); // 强保护，严禁使用任何数字处理法
    var file = sorted.find(function(f) { return String(f.id) === fileId; });

    row.onclick = function(e) {
      if (e.target.type === 'checkbox') return;
      if (e.target.closest('.file-actions')) return;
      if (e.ctrlKey || e.metaKey) {
        toggleSelect(fileId);
      } else {
        if (file && file.isDir === 1) {
          // 双击或单击进入文件夹
          STATE.currentParentId = fileId;
          var path = STATE.breadcrumbPath.slice();
          path.push({ id: fileId, name: file.fileName });
          updateBreadcrumb(path);
          loadFileList(fileId);
        }
      }
    };

    row.ondblclick = function(e) {
      if (file && file.isDir === 0) {
        downloadFile(file);
      }
    };

    row.oncontextmenu = function(e) {
      e.preventDefault();
      if (!STATE.selectedFiles.has(fileId)) {
        STATE.selectedFiles.clear();
        STATE.selectedFiles.add(fileId);
        $$('.file-item').forEach(function(r) { r.classList.remove('selected'); });
        row.classList.add('selected');
      }
      App.contextMenuTarget = { type: file && file.isDir === 1 ? 'folder' : 'file', id: fileId, item: file };
      showFileContextMenu(e.clientX, e.clientY);
    };
  });
}

function renderFileRow(f) {
  var isDir = f.isDir === 1;
  var icon = isDir ? '📁' : (CONFIG.FILE_ICONS[f.fileType] || '📄');
  var typeName = isDir ? '文件夹' : (CONFIG.FILE_TYPE_CN[f.fileType] || f.fileType || '未知');
  var sizeStr = isDir ? '-' : formatSize(f.fileSize || 0);
  var dateStr = f.gmtModified ? new Date(f.gmtModified).toLocaleString('zh-CN') : '-';
  return '<tr class="file-item" data-file-id="' + String(f.id) + '">' +
    '<td><input type="checkbox" class="file-select" data-id="' + String(f.id) + '"></td>' +
    '<td><span class="file-icon">' + icon + '</span>' +
      (isDir
        ? '<span class="file-name-link">' + escHtml(f.fileName) + '</span>'
        : '<span>' + escHtml(f.fileName) + '</span>') +
    '</td>' +
    '<td>' + typeName + '</td>' +
    '<td>' + sizeStr + '</td>' +
    '<td>' + dateStr + '</td>' +
    '<td class="file-actions">' +
      (isDir ? '' : '<button class="btn-sm btn-primary" onclick="event.stopPropagation();app.downloadFileById(\'' + String(f.id) + '\')">下载</button>') +
      '<button class="btn-sm" onclick="event.stopPropagation();app.renameFileById(\'' + String(f.id) + '\',\'' + escAttr(f.fileName) + '\')">重命名</button>' +
      '<button class="btn-sm danger" onclick="event.stopPropagation();app.deleteFileById(\'' + String(f.id) + '\')">删除</button>' +
    '</td>' +
  '</tr>';
}

function toggleSelect(fileId) {
  var targetId = String(fileId);
  if (STATE.selectedFiles.has(targetId)) {
    STATE.selectedFiles.delete(targetId);
  } else {
    STATE.selectedFiles.add(targetId);
  }
  $$('.file-item').forEach(function(r) {
    var fid = String(r.dataset.fileId);
    r.classList.toggle('selected', STATE.selectedFiles.has(fid));
    var cb = r.querySelector('.file-select');
    if (cb) cb.checked = STATE.selectedFiles.has(fid);
  });
  updateToolbar();
}

function showFileContextMenu(x, y) {
  var items = [
    { icon: '📥', label: '下载', action: 'download', handler: function() { batchDownload(); } },
    { icon: '✏️', label: '重命名', action: 'rename', handler: function(t) { window.app.renameFileById(String(t.id), t.item ? t.item.fileName || '' : ''); } },
    { icon: '📋', label: '复制', action: 'copy', handler: function() { showCopyMoveModal('copy'); } },
    { icon: '📦', label: '移动', action: 'move', handler: function() { showCopyMoveModal('move'); } },
    { icon: '🔗', label: '创建分享', action: 'share', handler: function() { App.showCreateShareModal(); } },
    '-',
    { icon: '🗑️', label: '删除', action: 'delete', handler: function() { batchDelete(); } },
  ];
  if (App.contextMenuTarget.type === 'folder') {
    items.unshift(
      { icon: '📁+', label: '新建子文件夹', action: 'new-folder', handler: function() { showNewFolderModal(String(App.contextMenuTarget.id)); } },
      { icon: '📤', label: '上传到此', action: 'upload-here', handler: function() { triggerFileUpload(String(App.contextMenuTarget.id)); } }
    );
  }
  showContextMenu(x, y, items);
}

// ==================== 工具栏 ====================
function updateToolbar() {
  var tb = $('#toolbar');
  var count = STATE.selectedFiles.size;
  // 注入核心补丁：在所有硬连接模板参数外层强制塞入单引号保护，隔绝数字解析
  tb.innerHTML =
    '<button onclick="app.triggerFileUpload(\'' + String(STATE.currentParentId) + '\')">📤 上传文件</button>' +
    '<button onclick="app.showNewFolderModal(\'' + String(STATE.currentParentId) + '\')">📁 新建文件夹</button>' +
    (count > 0 ?
      '<button onclick="app.batchDownload()">📥 下载 (' + count + ')</button>' +
      '<button onclick="app.batchDelete()" class="danger">🗑️ 删除 (' + count + ')</button>' +
      '<button onclick="app.showCopyMoveModal(\'copy\')">📋 复制 (' + count + ')</button>' +
      '<button onclick="app.showCopyMoveModal(\'move\')">📦 移动 (' + count + ')</button>' +
      '<button onclick="app.showCreateShareModal()">🔗 分享 (' + count + ')</button>'
    : '') +
    '<button onclick="app.refreshCurrentDir()">🔄 刷新</button>';
}

function setupToolbarButtons() {
  $('#btn-new-folder').onclick = function() { showNewFolderModal(String(STATE.currentParentId)); };
  $('#btn-upload-file').onclick = function() { triggerFileUpload(String(STATE.currentParentId)); };
  $('#btn-refresh').onclick = function() {
    loadFileList(STATE.currentParentId);
    loadFolderTree();
  };
}
App.setupToolbarButtons = setupToolbarButtons;

function setupFileSearch() {
  var searchInput = $('#file-search');
  var searchTimer;
  searchInput.oninput = function() {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(async function() {
      var keyword = searchInput.value.trim();
      if (!keyword) { loadFileList(STATE.currentParentId); return; }
      try {
        STATE.currentFiles = await API.searchFiles(keyword);
        renderFileList();
        terminalLog('搜索 "' + keyword + '": ' + STATE.currentFiles.length + ' 个结果', 'info');
      } catch (err) {
        toast('搜索失败: ' + err.message, 'error');
      }
    }, 300);
  };
}
App.setupFileSearch = setupFileSearch;

// ==================== 文件操作 ====================
function triggerFileUpload(parentId) {
  var input = document.createElement('input');
  input.type = 'file';
  input.multiple = true;
  input.onchange = function() {
    Array.from(input.files).forEach(function(file) { handleFileUpload(file, String(parentId)); });
  };
  input.click();
}

async function handleFileUpload(file, parentId) {
  terminalLog('开始上传: ' + file.name + ' (' + formatSize(file.size) + ')', 'info');
  try {
    var identifier = await computeFileHash(file);
    try {
      var secondResult = await API.secondUpload(file.name, identifier, parentId);
      if (secondResult === true) {
        terminalLog('秒传成功: ' + file.name, 'success');
        toast('秒传成功: ' + file.name, 'success');
        loadFileList(STATE.currentParentId);
        return;
      }
    } catch (e) { }

    if (file.size <= CONFIG.CHUNK_SIZE) {
      await API.uploadSmallFile(file.name, identifier, parentId, file.size, file);
      terminalLog('上传成功: ' + file.name, 'success');
      toast('上传成功: ' + file.name, 'success');
      loadFileList(STATE.currentParentId);
      return;
    }

    await chunkedUpload(file, parentId, identifier);
  } catch (err) {
    terminalLog('上传失败: ' + file.name + ' - ' + err.message, 'error');
    toast('上传失败: ' + err.message, 'error');
  }
}

async function computeFileHash(file) {
  var str = file.name + '|' + file.size + '|' + file.lastModified;
  var hash = 0;
  for (var i = 0; i < str.length; i++) {
    var c = str.charCodeAt(i);
    hash = ((hash << 5) - hash) + c;
    hash |= 0;
  }
  return Math.abs(hash).toString(16);
}

async function chunkedUpload(file, parentId, identifier) {
  var chunkSize = CONFIG.CHUNK_SIZE;
  var totalChunks = Math.ceil(file.size / chunkSize);

  var task = await API.initChunkTask(file.name, identifier, file.size, chunkSize);
  terminalLog('分片上传: ' + totalChunks + ' 个分片', 'info');

  for (var i = 0; i < totalChunks; i++) {
    var partNumber = i + 1;
    var start = i * chunkSize;
    var end = Math.min(start + chunkSize, file.size);
    var chunk = file.slice(start, end);

    var uploadUrl = await API.getChunkUploadUrl(identifier, partNumber);
    await fetch(uploadUrl, { method: 'PUT', body: chunk, headers: { 'Content-Type': 'application/octet-stream' } });
    terminalLog('分片 ' + partNumber + '/' + totalChunks + ' 上传完成', 'info');
  }

  await API.mergeChunks(identifier, parentId);
  terminalLog('文件合并完成: ' + file.name, 'success');
  toast('上传成功: ' + file.name, 'success');
  loadFileList(STATE.currentParentId);
}

function showNewFolderModal(parentId) {
  var targetParentId = parentId || STATE.currentParentId;
  showModal('新建文件夹',
    '<div class="form-group">' +
      '<label>文件夹名称</label>' +
      '<input type="text" id="new-folder-name" placeholder="输入文件夹名称">' +
    '</div>', async function() {
    var name = $('#new-folder-name').value.trim();
    if (!name) { toast('请输入文件夹名称', 'error'); return; }
    try {
      await API.createFolder(name, String(targetParentId));
      toast('文件夹创建成功', 'success');
      loadFileList(STATE.currentParentId);
      loadFolderTree();
    } catch (err) {
      toast('创建失败: ' + err.message, 'error');
    }
  }, '创建');
  setTimeout(function() { var el = $('#new-folder-name'); if (el) el.focus(); }, 100);
}

async function batchDelete() {
  var ids = Array.from(STATE.selectedFiles).map(function(id) { return String(id); });
  if (ids.length === 0) { toast('请先选择文件', 'info'); return; }
  showModal('删除文件', '<p>确定要删除选中的 ' + ids.length + ' 个文件吗？文件将移入回收站。</p>', async function() {
    try {
      await API.deleteFiles(ids);
      toast('已移入回收站', 'success');
      loadFileList(STATE.currentParentId);
    } catch (err) {
      toast('删除失败: ' + err.message, 'error');
    }
  }, '删除', true);
}

async function batchDownload() {
  var ids = Array.from(STATE.selectedFiles).map(function(id) { return String(id); });
  if (ids.length === 0) { toast('请先选择文件', 'info'); return; }
  try {
    var urls = await API.getDownloadUrls(ids);
    urls.forEach(function(item) {
      window.open(item.downloadUrl, '_blank');
    });
    toast('开始下载 ' + urls.length + ' 个文件', 'success');
  } catch (err) {
    toast('获取下载链接失败: ' + err.message, 'error');
  }
}

function showCopyMoveModal(mode) {
  showModal(mode === 'copy' ? '复制文件' : '移动文件',
    '<div class="form-group">' +
      '<label>目标文件夹 ID</label>' +
      '<input type="text" id="target-folder-id" placeholder="0 = 根目录" value="0">' +
    '</div>' +
    '<p style="font-size:12px;color:var(--text-secondary)">输入目标文件夹ID，0 表示根目录</p>', async function() {
    var targetId = String($('#target-folder-id').value).trim() || '0';
    var ids = Array.from(STATE.selectedFiles).map(function(id) { return String(id); });
    try {
      if (mode === 'copy') {
        await API.copyFiles(ids, targetId);
      } else {
        await API.moveFiles(ids, targetId);
      }
      toast(mode === 'copy' ? '复制成功' : '移动成功', 'success');
      loadFileList(STATE.currentParentId);
      loadFolderTree();
    } catch (err) {
      toast('操作失败: ' + err.message, 'error');
    }
  }, mode === 'copy' ? '复制' : '移动');
}

function batchCopyTo(targetId) { STATE.selectedFiles = new Set([String(App.contextMenuTarget.id)]); showCopyMoveModal('copy'); }
function batchMoveTo(targetId) {
  if (STATE.selectedFiles.size === 0) STATE.selectedFiles.add(String(App.contextMenuTarget ? App.contextMenuTarget.id : ''));
  showCopyMoveModal('move');
}

async function downloadFile(file) { await batchDownload(); }

function refreshCurrentDir() { loadFileList(STATE.currentParentId); loadFolderTree(); }
App.refreshCurrentDir = refreshCurrentDir;

// ==================== 右键菜单上下文 ====================
function setupContextMenuHandlers() {
  document.addEventListener('contextmenu', function(e) {
    if (e.target.closest('#content-area') && !e.target.closest('.file-item')) {
      e.preventDefault();
      App.contextMenuTarget = { type: 'blank', id: String(STATE.currentParentId) };
      showContextMenu(e.clientX, e.clientY, [
        { icon: '📁+', label: '新建文件夹', action: 'new-folder', handler: function() { showNewFolderModal(String(STATE.currentParentId)); } },
        { icon: '📤', label: '上传文件', action: 'upload', handler: function() { triggerFileUpload(String(STATE.currentParentId)); } },
        { icon: '🔄', label: '刷新', action: 'refresh', handler: refreshCurrentDir },
      ]);
    }
  });
}
App.setupContextMenuHandlers = setupContextMenuHandlers;

// ==================== 全局暴露（HTML onclick 调用） ====================
window.app.triggerFileUpload = triggerFileUpload;
window.app.showNewFolderModal = showNewFolderModal;
window.app.batchDownload = batchDownload;
window.app.batchDelete = batchDelete;
window.app.showCopyMoveModal = showCopyMoveModal;
window.app.refreshCurrentDir = refreshCurrentDir;
window.app.downloadFileById = async function(id) { STATE.selectedFiles = new Set([String(id)]); await batchDownload(); };
window.app.renameFileById = function(id, oldName) {
  showModal('重命名',
    '<div class="form-group">' +
      '<label>新名称</label>' +
      '<input type="text" id="rename-input" value="' + escAttr(oldName) + '">' +
    '</div>', async function() {
    var newName = $('#rename-input').value.trim();
    if (!newName) { toast('请输入新名称', 'error'); return; }
    try {
      await API.renameFile(String(id), newName);
      toast('重命名成功', 'success');
      loadFileList(STATE.currentParentId);
    } catch (err) {
      toast('重命名失败: ' + err.message, 'error');
    }
  }, '确定');
  setTimeout(function() { var el = $('#rename-input'); if (el) el.focus(); }, 100);
};
window.app.deleteFileById = function(id) { STATE.selectedFiles = new Set([String(id)]); batchDelete(); };

})();
