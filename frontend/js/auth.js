/**
 * DCloud AiPan - 认证模块
 * 登录、注册、自动登录
 */
var App = window.App;
(function() {
'use strict';

// ==================== 本地别名 ====================
var $ = App.$;
var $$ = App.$$;
var toast = App.toast;
var terminalLog = App.terminalLog;

// ==================== 认证 ====================
function initAuth() {
  var loginForm = $('#login-form');
  var registerForm = $('#register-form');
  var authTabs = $$('.auth-tab');

  authTabs.forEach(function(tab) {
    tab.onclick = function() {
      authTabs.forEach(function(t) { t.classList.remove('active'); });
      tab.classList.add('active');
      $$('.auth-form').forEach(function(f) { f.classList.remove('active'); });
      $('#' + tab.dataset.tab + '-form').classList.add('active');
    };
  });

  loginForm.onsubmit = async function(e) {
    e.preventDefault();
    var phone = $('#login-phone').value.trim();
    var password = $('#login-password').value.trim();
    if (!phone || !password) { $('#login-error').textContent = '请填写完整信息'; return; }
    try {
      await API.login(phone, password);
      $('#auth-overlay').style.display = 'none';
      $('#app').style.display = '';
      terminalLog('登录成功', 'success');
      await App.initApp();
    } catch (err) {
      $('#login-error').textContent = err.message;
      toast('登录失败: ' + err.message, 'error');
    }
  };

  registerForm.onsubmit = async function(e) {
    e.preventDefault();
    var name = $('#reg-name').value.trim();
    var phone = $('#reg-phone').value.trim();
    var password = $('#reg-password').value.trim();
    if (!name || !phone || !password) { $('#reg-error').textContent = '请填写完整信息'; return; }
    try {
      await API.register(name, password, phone);
      toast('注册成功，请登录', 'success');
      authTabs[0].click();
    } catch (err) {
      $('#reg-error').textContent = err.message;
      toast('注册失败: ' + err.message, 'error');
    }
  };

  // 自动登录
  if (API.token) {
    API.getUserDetail().then(function() {
      $('#auth-overlay').style.display = 'none';
      $('#app').style.display = '';
      App.initApp();
    }).catch(function() {
      API.clearToken();
    });
  }
}

App.initAuth = initAuth;

})();
