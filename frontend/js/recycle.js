/**
 * DCloud AiPan - 回收站模块
 * 回收站列表、还原文件、永久删除、批量操作
 */
var App = window.App;
(function() {
'use strict';

// ==================== 本地别名 ====================
var STATE = App.STATE;
var $ = App.$;
var toast = App.toast;
var showModal = App.showModal;
var formatSize = App.formatSize;
var escHtml = App.escHtml;

// ==================== 回收站列表 ====================
async function loadRecycleList() {
  try {
    var items = await API.getRecycleList();
    renderRecycleList(items);
    renderRecycleSide(items);
  } catch (err) {
    toast('加载回收站失败: ' + err.message, 'error');
  }
}
App.loadRecycleList = loadRecycleList;

function renderRecycleList(items) {
  var area = $('#content-area');
  if (!items || items.length === 0) {
    area.innerHTML = '<div class="empty-state"><p>回收站为空</p></div>';
    return;
  }
  area.innerHTML =
    '<table class="file-table">' +
      '<thead><tr><th>名称</th><th>类型</th><th>大小</th><th>删除时间</th><th>操作</th></tr></thead>' +
      '<tbody>' + items.map(function(f) {
        return '<tr>' +
          '<td>' + (f.isDir === 1 ? '📁' : (CONFIG.FILE_ICONS[f.fileType] || '📄')) + ' ' + escHtml(f.fileName) + '</td>' +
          '<td>' + (f.isDir === 1 ? '文件夹' : (CONFIG.FILE_TYPE_CN[f.fileType] || f.fileType)) + '</td>' +
          '<td>' + (f.isDir === 1 ? '-' : formatSize(f.fileSize || 0)) + '</td>' +
          '<td>' + (f.delTime ? new Date(f.delTime).toLocaleString('zh-CN') : '-') + '</td>' +
          '<td>' +
            '<button class="btn-sm btn-primary" onclick="app.restoreRecycleFile(\'' + String(f.id) + '\')">还原</button>' +
            '<button class="btn-sm danger" onclick="app.permanentDeleteFile(\'' + String(f.id) + '\')">彻底删除</button>' +
          '</td>' +
        '</tr>';
      }).join('') + '</tbody>' +
    '</table>';
}

function renderRecycleSide(items) {
  var container = $('#recycle-info');
  if (!container) return;
  if (!items || items.length === 0) {
    container.innerHTML = '<div style="padding:12px;color:var(--text-muted)">回收站为空</div>';
    return;
  }
  container.innerHTML = items.map(function(f) {
    return '<div class="recycle-item">' +
      '<div>' + escHtml(f.fileName) + '</div>' +
      '<div style="font-size:11px;color:var(--text-secondary)">' + (f.delTime ? new Date(f.delTime).toLocaleDateString('zh-CN') : '') + '</div>' +
    '</div>';
  }).join('');
}

// ==================== 回收站操作 ====================
async function restoreRecycleFile(fileId) {
  try {
    await API.restoreFiles([String(fileId)]);
    toast('文件已还原', 'success');
    loadRecycleList();
    App.loadFolderTree();
  } catch (err) { toast('还原失败: ' + err.message, 'error'); }
}
window.app.restoreRecycleFile = restoreRecycleFile;

async function permanentDeleteFile(fileId) {
  showModal('永久删除', '<p>此操作不可逆，确定要永久删除该文件吗？</p>', async function() {
    try {
      await API.permanentlyDelete([String(fileId)]);
      toast('文件已永久删除', 'success');
      loadRecycleList();
    } catch (err) { toast('删除失败: ' + err.message, 'error'); }
  }, '永久删除', true);
}
window.app.permanentDeleteFile = permanentDeleteFile;

// 回收站全部操作按钮
var btnRestoreAll = $('#btn-restore-all');
if (btnRestoreAll) {
  btnRestoreAll.addEventListener('click', async function() {
    var items = await API.getRecycleList();
    if (!items || items.length === 0) { toast('回收站为空', 'info'); return; }
    try {
      await API.restoreFiles(items.map(function(f) { return String(f.id); }));
      toast('全部文件已还原', 'success');
      loadRecycleList();
    } catch (err) { toast('还原失败: ' + err.message, 'error'); }
  });
}

var btnDeleteAll = $('#btn-delete-all');
if (btnDeleteAll) {
  btnDeleteAll.addEventListener('click', async function() {
    var items = await API.getRecycleList();
    if (!items || items.length === 0) { toast('回收站为空', 'info'); return; }
    showModal('清空回收站', '<p>此操作不可逆，确定要永久删除回收站中的所有文件吗？</p>', async function() {
      try {
        await API.permanentlyDelete(items.map(function(f) { return String(f.id); }));
        toast('回收站已清空', 'success');
        loadRecycleList();
      } catch (err) { toast('清空失败: ' + err.message, 'error'); }
    }, '清空回收站', true);
  });
}

})();
