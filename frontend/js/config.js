// DCloud AiPan Frontend Configuration
const CONFIG = {
  // 通过 Node 代理，使用相对路径，无需配置跨域
  BACKEND_BASE: '',
  AI_AGENT_BASE: '/ai-agent',

  // API 路径
  API: {
    // 账号
    LOGIN: '/api/account/v1/login',
    REGISTER: '/api/account/v1/register',
    USER_DETAIL: '/api/account/v1/detail',
    AVATAR_UPLOAD: '/api/account/v1/avatar/upload',

    // 文件
    FILE_LIST: '/api/file/v1/list',
    FOLDER_CREATE: '/api/file/v1/create_folder',
    FILE_RENAME: '/api/file/v1/renameFile',
    FOLDER_TREE: '/api/file/v1/folder/tree',
    FILE_UPLOAD: '/api/file/v1/upload',
    FILE_MOVE: '/api/file/v1/move_batch',
    FILE_DELETE: '/api/file/v1/delete_batch',
    FILE_COPY: '/api/file/v1/copy_batch',
    SECOND_UPLOAD: '/api/file/v1/second_upload',
    CHUNK_INIT: '/api/file/v1/init_file_chunk_task',
    CHUNK_UPLOAD_URL: '/api/file/v1/get_file_chunk_upload_url',
    CHUNK_MERGE: '/api/file/v1/merge_file_chunk',
    CHUNK_PROGRESS: '/api/file/v1/chunk_upload_progress',
    FILE_SEARCH: '/api/file/v1/search',
    FILE_DOWNLOAD_URL: '/api/file/v1/batch_download_url',

    // 分享
    SHARE_LIST: '/api/share/v1/list',
    SHARE_CREATE: '/api/share/v1/create',
    SHARE_CANCEL: '/api/share/v1/cancel',
    SHARE_VISIT: '/api/share/v1/visit',
    SHARE_CHECK_CODE: '/api/share/v1/check_share_code',
    SHARE_DETAIL: '/api/share/v1/detail',
    SHARE_FILE_LIST: '/api/share/v1/file_share_list',
    SHARE_TRANSFER: '/api/share/v1/transfer',

    // 回收站
    RECYCLE_LIST: '/api/recycle/v1/list',
    RECYCLE_DELETE: '/api/recycle/v1/delete',
    RECYCLE_RESTORE: '/api/recycle/v1/restore',

    // AI 聊天 (Spring Boot 代理)
    AI_CHAT_STREAM: '/ai/chat/stream'
  },

  // AI 智能体 API (通过 Node 代理 /ai-agent -> Python 8000)
  AI: {
    CHAT_STREAM: '/api/chat/stream',
    DOC_STREAM: '/api/document/stream',
    PAN_QUERY: '/api/pan/query'
  },

  // 文件类型图标映射
  FILE_ICONS: {
    'common': '📄', 'compress': '📦', 'excel': '📊', 'word': '📝',
    'pdf': '📕', 'txt': '📃', 'img': '🖼️', 'audio': '🎵',
    'video': '🎬', 'ppt': '📽️', 'code': '💻', 'csv': '📋',
    'dir': '📁'
  },

  // 分享有效期
  SHARE_DAY_TYPES: {
    0: '永久有效',
    1: '7天有效',
    2: '30天有效'
  },

  // 分享状态
  SHARE_STATUS: {
    'used': '正常',
    'expired': '已过期',
    'canceled': '已取消'
  },

  // 文件类型中文
  FILE_TYPE_CN: {
    'common': '普通文件', 'compress': '压缩包', 'excel': 'Excel', 'word': 'Word',
    'pdf': 'PDF', 'txt': '文本', 'img': '图片', 'audio': '音频',
    'video': '视频', 'ppt': 'PPT', 'code': '代码', 'csv': 'CSV'
  },

  // 分片上传配置
  CHUNK_SIZE: 5 * 1024 * 1024  // 5MB per chunk
};

// 导出
if (typeof module !== 'undefined') { module.exports = CONFIG; }
