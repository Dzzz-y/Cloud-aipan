/**
 * DCloud AiPan - 公共模块
 * 状态管理、DOM工具、通知、模态框、右键菜单、工具函数
 */
var App = window.App || {};
(function() {
'use strict';

var A = App;

// ==================== 状态管理 ====================
A.STATE = {
  user: null,
  currentPanel: 'files',
  currentParentId: '0', // 统一使用字符串管理 ID，彻底绝后患
  breadcrumbPath: [{ id: '0', name: '根目录' }],
  selectedFiles: new Set(),
  folderTree: [],
  currentFiles: [],
  currentTab: 'files',
  // AI 对话
  chatMessages: [],
  isChatStreaming: false,
  // 分享浏览
  shareBrowseState: null
};

// ==================== Dom 工具 ====================
A.$ = function(sel, ctx) { return (ctx || document).querySelector(sel); };
A.$$ = function(sel, ctx) { return Array.from((ctx || document).querySelectorAll(sel)); };

// ==================== Toast 通知 ====================
(function() {
  var container = document.createElement('div');
  container.className = 'toast-container';
  document.body.appendChild(container);
  A._toastContainer = container;
})();

A.toast = function(msg, type) {
  type = type || 'info';
  var el = document.createElement('div');
  el.className = 'toast toast-' + type;
  el.textContent = msg;
  A._toastContainer.appendChild(el);
  setTimeout(function() { el.remove(); }, 3000);
};

// ==================== 终端输出 ====================
A.terminalLog = function(msg, level) {
  level = level || 'info';
  var term = A.$('#terminal-output');
  if (!term) return;
  var line = document.createElement('div');
  line.className = 'log-line log-' + level;
  line.textContent = '[' + new Date().toLocaleTimeString() + '] ' + msg;
  term.appendChild(line);
  term.scrollTop = term.scrollHeight;
};

// ==================== 模态框 ====================
A.showModal = function(title, contentHtml, onConfirm, confirmText, danger) {
  confirmText = confirmText || '确定';
  danger = danger || false;
  var overlay = A.$('#modal-overlay');
  var content = A.$('#modal-content');
  content.innerHTML =
    '<h3>' + title + '</h3>' +
    '<div class="modal-body">' + contentHtml + '</div>' +
    '<div class="modal-actions">' +
      '<button class="btn-cancel" id="modal-cancel">取消</button>' +
      '<button class="' + (danger ? 'btn-danger' : 'btn-primary') + '" id="modal-confirm">' + confirmText + '</button>' +
    '</div>';
  overlay.style.display = 'flex';
  A.$('#modal-cancel').onclick = function() { overlay.style.display = 'none'; };
  A.$('#modal-confirm').onclick = function() {
    overlay.style.display = 'none';
    if (onConfirm) onConfirm();
  };
  overlay.onclick = function(e) { if (e.target === overlay) overlay.style.display = 'none'; };
};

A.hideModal = function() { A.$('#modal-overlay').style.display = 'none'; };

// ==================== 右键菜单 ====================
A.contextMenuTarget = null;

A.showContextMenu = function(x, y, items) {
  var menu = A.$('#context-menu');
  menu.innerHTML = items.map(function(item) {
    if (item === '-') return '<div class="context-menu-divider"></div>';
    return '<div class="context-menu-item" data-action="' + item.action + '">' +
      '<span>' + (item.icon || '') + '</span><span>' + item.label + '</span></div>';
  }).join('');
  menu.style.display = 'block';
  menu.style.left = Math.min(x, window.innerWidth - 200) + 'px';
  menu.style.top = Math.min(y, window.innerHeight - menu.scrollHeight - 20) + 'px';
  A.$$('.context-menu-item', menu).forEach(function(el) {
    el.onclick = function() {
      menu.style.display = 'none';
      var action = el.dataset.action;
      var found = items.find(function(i) { return i.action === action; });
      if (found && found.handler) found.handler(A.contextMenuTarget);
    };
  });
};

document.addEventListener('click', function() { A.$('#context-menu').style.display = 'none'; });

// ==================== 工具函数 ====================
A.formatSize = function(bytes) {
  if (!bytes || bytes === 0) return '0 B';
  var units = ['B', 'KB', 'MB', 'GB', 'TB'];
  var i = Math.floor(Math.log(bytes) / Math.log(1024));
  return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
};

A.escHtml = function(str) {
  if (!str) return '';
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
};

A.escAttr = function(str) {
  if (!str) return '';
  return String(str).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
};

// ==================== 图片灯箱 ====================
A.initLightbox = function() {
  if (document.getElementById('image-lightbox')) return;
  var lb = document.createElement('div');
  lb.id = 'image-lightbox';
  lb.className = 'image-lightbox';
  lb.innerHTML =
    '<div class="lightbox-backdrop"></div>' +
    '<div class="lightbox-content">' +
      '<button class="lightbox-close" title="关闭">✕</button>' +
      '<button class="lightbox-prev" title="上一张">‹</button>' +
      '<button class="lightbox-next" title="下一张">›</button>' +
      '<img class="lightbox-img" src="" alt="">' +
      '<div class="lightbox-info">' +
        '<span class="lightbox-filename"></span>' +
        '<span class="lightbox-counter"></span>' +
        '<a class="lightbox-download btn-sm btn-primary" href="" download>⬇ 下载原图</a>' +
      '</div>' +
    '</div>';
  document.body.appendChild(lb);

  var currentIndex = 0;
  var images = [];

  lb.querySelector('.lightbox-backdrop').onclick = A.hideLightbox;
  lb.querySelector('.lightbox-close').onclick = A.hideLightbox;
  lb.querySelector('.lightbox-prev').onclick = function() {
    currentIndex = (currentIndex - 1 + images.length) % images.length;
    A._showLightboxImage(images[currentIndex]);
    lb._setIndex(currentIndex);
  };
  lb.querySelector('.lightbox-next').onclick = function() {
    currentIndex = (currentIndex + 1) % images.length;
    A._showLightboxImage(images[currentIndex]);
    lb._setIndex(currentIndex);
  };

  lb._setImages = function(imgs) { images = imgs; };
  lb._setIndex = function(i) { currentIndex = i; };
  lb._getImages = function() { return images; };
  lb._getIndex = function() { return currentIndex; };

  // 键盘导航
  document.addEventListener('keydown', function(e) {
    if (lb.style.display !== 'flex') return;
    if (e.key === 'Escape') A.hideLightbox();
    if (e.key === 'ArrowLeft') lb.querySelector('.lightbox-prev').click();
    if (e.key === 'ArrowRight') lb.querySelector('.lightbox-next').click();
  });
};

A._showLightboxImage = function(img) {
  var lb = document.getElementById('image-lightbox');
  if (!lb) return;
  lb.querySelector('.lightbox-img').src = img.url;
  lb.querySelector('.lightbox-filename').textContent = img.name || '';
  lb.querySelector('.lightbox-download').href = img.url;
  lb.querySelector('.lightbox-download').download = img.name || '';
  var idx = lb._getIndex();
  var imgs = lb._getImages();
  lb.querySelector('.lightbox-counter').textContent = imgs.length > 1 ? (idx + 1) + ' / ' + imgs.length : '';
};

A.showLightbox = function(images, startIndex) {
  A.initLightbox();
  var lb = document.getElementById('image-lightbox');
  lb._setImages(images);
  lb._setIndex(startIndex || 0);
  A._showLightboxImage(images[startIndex || 0]);
  lb.style.display = 'flex';
};

A.hideLightbox = function() {
  var lb = document.getElementById('image-lightbox');
  if (lb) lb.style.display = 'none';
};

// ==================== 图片卡片渲染（pan 查询结果通用） ====================
// 异步加载文件预览（图片缩略图 / 其他文件表格）
A.renderPanFileCards = async function(container, fileList) {
  if (!container || !fileList || fileList.length === 0) return;

  var imgFiles = [];
  var otherFiles = [];
  fileList.forEach(function(f) {
    if (f.file_type && f.file_type.toLowerCase() === 'img') {
      imgFiles.push(f);
    } else {
      otherFiles.push(f);
    }
  });

  var html = '';

  // 图片网格
  if (imgFiles.length > 0) {
    html += '<div class="pan-gallery-label">🖼️ 图片文件 (' + imgFiles.length + ')</div>';
    html += '<div class="pan-image-gallery" id="pan-gallery-grid">';

    var imgIds = imgFiles.map(function(f) { return String(f.id || f.file_id); });
    try {
      var urls = await API.getDownloadUrls(imgIds);
      var urlMap = {};
      (urls || []).forEach(function(item, i) {
        var u = typeof item === 'string' ? item : (item.downloadUrl || item.url || '');
        if (u && imgFiles[i]) {
          urlMap[String(imgFiles[i].id || imgFiles[i].file_id)] = u;
        }
      });

      imgFiles.forEach(function(f) {
        var fid = String(f.id || f.file_id);
        var url = urlMap[fid] || '';
        var displayUrl = url ? A.escAttr(url) : '';
        html += '<div class="pan-image-card' + (url ? ' has-image' : '') + '" data-img-url="' + displayUrl + '" data-img-name="' + A.escAttr(f.file_name || '') + '">';
        if (url) {
          html += '<div class="pan-image-thumb"><img src="' + displayUrl + '" alt="' + A.escAttr(f.file_name) + '" loading="lazy"></div>';
        } else {
          html += '<div class="pan-image-thumb pan-image-placeholder">🖼️</div>';
        }
        html += '<div class="pan-image-name" title="' + A.escAttr(f.file_name || '') + '">' + A.escHtml(f.file_name || '未知') + '</div>';
        html += '<div class="pan-image-size">' + A.formatSize(f.file_size || 0) + '</div>';
        html += '<div class="pan-image-actions">' +
          (url ? '<a class="btn-sm btn-primary" href="' + displayUrl + '" download="' + A.escAttr(f.file_name || '') + '">⬇ 下载</a>' : '') +
        '</div>';
        html += '</div>';
      });
    } catch (e) {
      imgFiles.forEach(function(f) {
        html += '<div class="pan-image-card">' +
          '<div class="pan-image-thumb pan-image-placeholder">🖼️</div>' +
          '<div class="pan-image-name">' + A.escHtml(f.file_name || '未知') + '</div>' +
          '<div class="pan-image-size">' + A.formatSize(f.file_size || 0) + '</div>' +
        '</div>';
      });
    }
    html += '</div>';
  }

  // 非图片文件表格
  if (otherFiles.length > 0) {
    html += '<div class="pan-gallery-label" style="margin-top:16px;">📄 其他文件 (' + otherFiles.length + ')</div>';
    html += '<table class="file-table"><thead><tr><th>名称</th><th>类型</th><th>大小</th><th>修改时间</th></tr></thead><tbody>';
    otherFiles.forEach(function(f) {
      html += '<tr>' +
        '<td>' + (CONFIG.FILE_ICONS[f.file_type] || '📄') + ' ' + A.escHtml(f.file_name) + '</td>' +
        '<td>' + (CONFIG.FILE_TYPE_CN[f.file_type] || f.file_type || '-') + '</td>' +
        '<td>' + A.formatSize(f.file_size || 0) + '</td>' +
        '<td>' + (f.gmt_modified ? new Date(f.gmt_modified).toLocaleString('zh-CN') : '-') + '</td>' +
      '</tr>';
    });
    html += '</tbody></table>';
  }

  container.innerHTML = html;

  // 绑定图片卡片 → 灯箱
  if (imgFiles.length > 0) {
    var cards = container.querySelectorAll('.pan-image-card.has-image');
    cards.forEach(function(card, idx) {
      card.style.cursor = 'pointer';
      card.onclick = function() {
        var imgs = [];
        container.querySelectorAll('.pan-image-card.has-image').forEach(function(c) {
          imgs.push({ url: c.dataset.imgUrl, name: c.dataset.imgName });
        });
        A.showLightbox(imgs, idx);
      };
    });
  }
};

// 全局可访问的 app 对象（供 HTML onclick 使用）
window.app = {};

})();
