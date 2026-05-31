// DCloud AiPan API Service Layer
class ApiService {
  constructor() {
    this.token = localStorage.getItem('dcloud_token') || '';
    this.shareToken = '';
  }

  setToken(token) { this.token = token; localStorage.setItem('dcloud_token', token); }
  clearToken() { this.token = ''; this.shareToken = ''; localStorage.removeItem('dcloud_token'); }
  setShareToken(t) { this.shareToken = t; }

  // 通用请求
  async request(method, url, body = null, opts = {}) {
    const headers = {};
    const baseUrl = opts.useAiAgent ? CONFIG.AI_AGENT_BASE : CONFIG.BACKEND_BASE;

    if (body && !(body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
    }
    if (this.token && !opts.noAuth) {
      headers['token'] = this.token;
    }
    if (this.shareToken && opts.useShareToken) {
      headers['share-token'] = this.shareToken;
    }
    if (opts.extraHeaders) {
      Object.assign(headers, opts.extraHeaders);
    }

    const fetchOpts = { method, headers };
    if (body) {
      fetchOpts.body = body instanceof FormData ? body : JSON.stringify(body);
    }
    if (opts.signal) fetchOpts.signal = opts.signal;

    const res = await fetch(baseUrl + url, fetchOpts);
    const json = await res.json();
    if (json.code !== undefined && json.code !== 0 && !opts.raw) {
      throw new Error(json.msg || '请求失败');
    }
    return opts.raw ? json : json.data;
  }

  get(url, opts) { return this.request('GET', url, null, opts); }
  post(url, body, opts) { return this.request('POST', url, body, opts); }

  // ==================== 账号 ====================
  async login(phone, password) {
    const data = await this.post(CONFIG.API.LOGIN, { phone, password }, { noAuth: true });
    this.setToken(data);
    return data;
  }

  async register(name, password, phone) {
    return this.post(CONFIG.API.REGISTER, { name, password, phone }, { noAuth: true });
  }

  async getUserDetail() {
    const data = await this.get(CONFIG.API.USER_DETAIL);
    this._userInfo = data;
    return data;
  }

  async uploadAvatar(file) {
    const fd = new FormData();
    fd.append('file', file);
    return this.post(CONFIG.API.AVATAR_UPLOAD, fd);
  }

  // ==================== 文件 ====================
  async getFileList(parentId = 0) {
    return this.get(CONFIG.API.FILE_LIST + '?parent_id=' + parentId);
  }

  async createFolder(folderName, parentId) {
    return this.post(CONFIG.API.FOLDER_CREATE, { folderName, parentId });
  }

  async renameFile(fileId, newFileName) {
    return this.post(CONFIG.API.FILE_RENAME, { fileId, newFileName });
  }

  async getFolderTree() {
    return this.get(CONFIG.API.FOLDER_TREE);
  }

  async uploadSmallFile(fileName, identifier, parentId, fileSize, file) {
    const fd = new FormData();
    fd.append('fileName', fileName);
    fd.append('identifier', identifier);
    fd.append('parentId', parentId);
    fd.append('fileSize', fileSize);
    fd.append('file', file);
    return this.post(CONFIG.API.FILE_UPLOAD, fd);
  }

  async moveFiles(fileIds, targetParentId) {
    return this.post(CONFIG.API.FILE_MOVE, { fileIds, targetParentId });
  }

  async deleteFiles(fileIds) {
    return this.post(CONFIG.API.FILE_DELETE, { fileIds });
  }

  async copyFiles(fileIds, targetParentId) {
    return this.post(CONFIG.API.FILE_COPY, { fileIds, targetParentId });
  }

  async secondUpload(fileName, identifier, parentId) {
    return this.post(CONFIG.API.SECOND_UPLOAD, { fileName, identifier, parentId });
  }

  async initChunkTask(fileName, identifier, totalSize, chunkSize) {
    return this.post(CONFIG.API.CHUNK_INIT, { fileName, identifier, totalSize, chunkSize });
  }

  async getChunkUploadUrl(identifier, partNumber) {
    return this.get(CONFIG.API.CHUNK_UPLOAD_URL + '/' + identifier + '/' + partNumber);
  }

  async mergeChunks(identifier, parentId) {
    return this.post(CONFIG.API.CHUNK_MERGE, { identifier, parentId });
  }

  async getChunkProgress(identifier) {
    return this.get(CONFIG.API.CHUNK_PROGRESS + '/' + identifier);
  }

  async searchFiles(keyword) {
    return this.get(CONFIG.API.FILE_SEARCH + '?search=' + encodeURIComponent(keyword));
  }

  async getDownloadUrls(fileIds) {
    return this.post(CONFIG.API.FILE_DOWNLOAD_URL, { fileIds });
  }

  // ==================== 分享 ====================
  async getShareList() {
    return this.get(CONFIG.API.SHARE_LIST);
  }

  async createShare(shareName, shareType, shareDayType, fileIds) {
    return this.post(CONFIG.API.SHARE_CREATE, { shareName, shareType, shareDayType, fileIds });
  }

  async cancelShare(shareIds) {
    return this.post(CONFIG.API.SHARE_CANCEL, { shareIds });
  }

  async visitShare(shareId) {
    return this.get(CONFIG.API.SHARE_VISIT + '?shareId=' + shareId, { noAuth: true });
  }

  async checkShareCode(shareId, shareCode) {
    return this.post(CONFIG.API.SHARE_CHECK_CODE, { shareId, shareCode }, { noAuth: true });
  }

  async getShareDetail() {
    return this.get(CONFIG.API.SHARE_DETAIL, { useShareToken: true });
  }

  async getShareFileList(parentId) {
    return this.post(CONFIG.API.SHARE_FILE_LIST, { parentId }, { useShareToken: true });
  }

  async transferShareFiles(parentId, fileIds) {
    return this.post(CONFIG.API.SHARE_TRANSFER, { parentId, fileIds }, { useShareToken: true });
  }

  // ==================== 回收站 ====================
  async getRecycleList() {
    return this.get(CONFIG.API.RECYCLE_LIST);
  }

  async permanentlyDelete(fileIdList) {
    return this.post(CONFIG.API.RECYCLE_DELETE, { fileIdList });
  }

  async restoreFiles(fileIds) {
    return this.post(CONFIG.API.RECYCLE_RESTORE, { fileIds });
  }

  // ==================== AI 对话 (SSE 流) ====================
  async chatStream(message, onChunk, onDone, onError) {
    const url = CONFIG.BACKEND_BASE + CONFIG.API.AI_CHAT_STREAM;
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'token': this.token },
        body: JSON.stringify(message)
      });
      if (!res.ok) throw new Error('AI服务连接失败: ' + res.status);
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed || !trimmed.startsWith('data:')) continue;
          const dataStr = trimmed.slice(5).trim();
          if (dataStr === '[DONE]') { onDone(); return; }
          try {
            const json = JSON.parse(dataStr);
            if (json.code === 0 && json.data) {
              onChunk(json.data);
            }
          } catch (e) { /* skip parse errors */ }
        }
      }
      onDone();
    } catch (e) {
      onError(e.message);
    }
  }

  // ==================== AI 文档总结 (SSE 流, 直达 AI Agent) ====================
  async documentStream(url, summaryType, language, length, instructions, onChunk, onDone, onError) {
    const fetchUrl = CONFIG.AI_AGENT_BASE + CONFIG.AI.DOC_STREAM;
    try {
      const body = { url, summary_type: summaryType, language, length, additional_instructions: instructions };
      const res = await fetch(fetchUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      if (!res.ok) throw new Error('AI文档服务连接失败: ' + res.status);
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed || !trimmed.startsWith('data:')) continue;
          const dataStr = trimmed.slice(5).trim();
          if (dataStr === '[DONE]') { onDone(); return; }
          try {
            const json = JSON.parse(dataStr);
            if (json.code === 0 && json.data) {
              onChunk(json.data);
            }
          } catch (e) { /* skip */ }
        }
      }
      onDone();
    } catch (e) {
      onError(e.message);
    }
  }

  // ==================== AI 网盘查询 ====================
  async panQuery(query) {
    return this.post(CONFIG.AI.PAN_QUERY, { query }, { useAiAgent: true });
  }
}

// 全局实例
const API = new ApiService();
