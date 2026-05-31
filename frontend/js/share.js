/**
 * DCloud AiPan - 分享管理模块
 * 分享列表、创建分享、访问分享、浏览分享、转存文件、取消分享
 */
var App = window.App;
(function() {
'use strict';

// ==================== 本地别名 ====================
var STATE = App.STATE;
var $ = App.$;
var toast = App.toast;
var terminalLog = App.terminalLog;
var showModal = App.showModal;
var escHtml = App.escHtml;
var escAttr = App.escAttr;

// ==================== 分享列表 ====================
async function loadShareList() {
  try {
    var shares = await API.getShareList();
    renderShareList(shares);
    renderShareListSide(shares);
  } catch (err) {
    toast('加载分享列表失败: ' + err.message, 'error');
  }
}
App.loadShareList = loadShareList;

function renderShareList(shares) {
  var area = $('#content-area');
  if (!shares || shares.length === 0) {
    area.innerHTML = '<div class="empty-state"><p>暂无分享</p></div>';
    return;
  }
  area.innerHTML =
    '<table class="file-table">' +
      '<thead><tr>' +
        '<th>分享名称</th><th>类型</th><th>有效期</th><th>状态</th><th>链接</th><th>操作</th>' +
      '</tr></thead>' +
      '<tbody>' + shares.map(function(s) {
        return '<tr>' +
          '<td>' + escHtml(s.shareName) + '</td>' +
          '<td>' + (s.shareType === 'need_code' ? '需提取码' : '公开') + '</td>' +
          '<td>' + (CONFIG.SHARE_DAY_TYPES[s.shareDayType] || '-') + '</td>' +
          '<td><span class="tag tag-' + (s.shareStatus === 'used' ? 'success' : (s.shareStatus === 'expired' ? 'warning' : 'error')) + '">' + (CONFIG.SHARE_STATUS[s.shareStatus] || s.shareStatus) + '</span></td>' +
          '<td style="max-width:200px;overflow:hidden;text-overflow:ellipsis" title="' + escAttr(s.shareUrl || '') + '">' + escHtml(s.shareUrl || '-') + '</td>' +
          '<td>' +
            (s.shareStatus === 'used' ? '<button class="btn-sm danger" onclick="app.cancelShare(\'' + String(s.id) + '\')">取消</button>' : '') +
            '<button class="btn-sm" onclick="app.visitShareModal(\'' + String(s.id) + '\')">查看</button>' +
          '</td>' +
        '</tr>';
      }).join('') + '</tbody>' +
    '</table>';
}

function renderShareListSide(shares) {
  var container = $('#share-list-side');
  if (!container) return;
  if (!shares || shares.length === 0) {
    container.innerHTML = '<div style="padding:12px;color:var(--text-muted)">暂无分享</div>';
    return;
  }
  container.innerHTML = shares.map(function(s) {
    return '<div class="share-item">' +
      '<div style="font-weight:600">' + escHtml(s.shareName) + '</div>' +
      '<div style="font-size:11px;color:var(--text-secondary)">' + (CONFIG.SHARE_DAY_TYPES[s.shareDayType] || '') + ' | <span class="tag tag-' + (s.shareStatus === 'used' ? 'success' : 'warning') + '">' + (CONFIG.SHARE_STATUS[s.shareStatus]) + '</span></div>' +
    '</div>';
  }).join('');
}

// ==================== 创建分享 ====================
function showCreateShareModal() {
  var ids = Array.from(STATE.selectedFiles).map(function(id) { return String(id); });
  if (ids.length === 0) { toast('请先选择要分享的文件', 'info'); return; }
  showModal('创建分享',
    '<div class="form-group"><label>分享名称</label><input type="text" id="share-name" placeholder="输入分享名称"></div>' +
    '<div class="form-group"><label>分享类型</label><select id="share-type"><option value="no_code">公开（无需提取码）</option><option value="need_code">私密（需要提取码）</option></select></div>' +
    '<div class="form-group"><label>有效期</label><select id="share-day-type"><option value="0">永久有效</option><option value="1">7天有效</option><option value="2">30天有效</option></select></div>' +
    '<p style="font-size:12px;color:var(--text-secondary)">已选择 ' + ids.length + ' 个文件</p>', async function() {
    var shareName = $('#share-name').value.trim();
    var shareType = $('#share-type').value;
    var shareDayType = parseInt($('#share-day-type').value);
    if (!shareName) { toast('请输入分享名称', 'error'); return; }
    try {
      var result = await API.createShare(shareName, shareType, shareDayType, ids);
      toast('分享创建成功!', 'success');
      if (result.shareCode) {
        terminalLog('提取码: ' + result.shareCode, 'warn');
        alert('分享创建成功！\n提取码: ' + result.shareCode + '\n链接: ' + (result.shareUrl || ''));
      }
      loadShareList();
    } catch (err) {
      toast('创建分享失败: ' + err.message, 'error');
    }
  }, '创建分享');
}
App.showCreateShareModal = showCreateShareModal;
window.app.showCreateShareModal = showCreateShareModal;

// ==================== 访问分享 ====================
async function visitShareModal(shareId) {
  try {
    var simple = await API.visitShare(String(shareId));
    if (simple.shareType === 'need_code') {
      showModal('输入提取码',
        '<div class="form-group"><label>提取码</label><input type="text" id="share-code-input" placeholder="输入分享提取码"></div>', async function() {
        var code = $('#share-code-input').value.trim();
        try {
          var token = await API.checkShareCode(String(shareId), code);
          API.setShareToken(token);
          toast('验证成功', 'success');
          await browseShare(shareId);
        } catch (err) { toast('提取码错误: ' + err.message, 'error'); }
      }, '验证');
      setTimeout(function() { var el = $('#share-code-input'); if (el) el.focus(); }, 100);
    } else {
      if (simple.shareToken) {
        API.setShareToken(simple.shareToken);
      }
      await browseShare(shareId);
    }
  } catch (err) {
    toast('访问分享失败: ' + err.message, 'error');
  }
}
window.app.visitShareModal = visitShareModal;

async function browseShare(shareId) {
  try {
    var detail = await API.getShareDetail();
    STATE.shareBrowseState = { shareId: String(shareId), detail: detail };
    var files = detail.fileDTOList || [];
    var area = $('#content-area');
    area.innerHTML =
      '<h3 style="padding:16px">📎 ' + escHtml(detail.shareName || '分享') + ' (来自: ' + (detail.shareAccountDTO ? detail.shareAccountDTO.userName || '未知' : '未知') + ')</h3>' +
      '<table class="file-table">' +
        '<thead><tr><th>名称</th><th>类型</th><th>大小</th><th>操作</th></tr></thead>' +
        '<tbody>' + files.map(function(f) {
          return '<tr>' +
            '<td>' + (f.isDir === 1 ? '📁' : (CONFIG.FILE_ICONS[f.fileType] || '📄')) + ' ' + escHtml(f.fileName) + '</td>' +
            '<td>' + (f.isDir === 1 ? '文件夹' : (CONFIG.FILE_TYPE_CN[f.fileType] || f.fileType)) + '</td>' +
            '<td>' + (f.isDir === 1 ? '-' : App.formatSize(f.fileSize || 0)) + '</td>' +
            '<td><button class="btn-sm btn-primary" onclick="app.transferShareFile(\'' + String(f.id) + '\')">转存</button></td>' +
          '</tr>';
        }).join('') + '</tbody>' +
      '</table>';
  } catch (err) {
    toast('获取分享详情失败: ' + err.message, 'error');
  }
}

async function transferShareFile(fileId) {
  showModal('转存文件',
    '<div class="form-group"><label>目标文件夹 ID</label><input type="text" id="transfer-target" value="0" placeholder="0 = 根目录"></div>', async function() {
    var parentId = String($('#transfer-target').value).trim() || '0';
    try {
      await API.transferShareFiles(parentId, [String(fileId)]);
      toast('转存成功', 'success');
    } catch (err) {
      toast('转存失败: ' + err.message, 'error');
    }
  }, '转存');
}
window.app.transferShareFile = transferShareFile;

async function cancelShare(shareId) {
  showModal('取消分享', '<p>确定要取消这个分享吗？</p>', async function() {
    try {
      await API.cancelShare([String(shareId)]);
      toast('分享已取消', 'success');
      loadShareList();
    } catch (err) {
      toast('取消失败: ' + err.message, 'error');
    }
  }, '确定', true);
}
window.app.cancelShare = cancelShare;

})();
