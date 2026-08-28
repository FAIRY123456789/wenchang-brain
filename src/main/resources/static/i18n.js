(function () {
  'use strict';

  const STORAGE_KEY = 'wenchang-system-language';
  const DEFAULT_LANGUAGE = 'zh-CN';
  const SUPPORTED = ['zh-CN', 'en', 'id', 'ar', 'pt'];
  const LANGUAGE_NAMES = {
    'zh-CN': '中文', en: 'English', id: 'Bahasa Indonesia', ar: 'العربية', pt: 'Português'
  };
  let activeLanguage = DEFAULT_LANGUAGE;

  const zh = {
    'meta.title': '文昌智脑 · 从海岸向星辰提问',
    'meta.description': '文昌智脑：连接文昌知识库、实时信息与智能模型的文昌城市知识智能体。',
    'a11y.skipToComposer': '跳到提问框', 'a11y.conversationNav': '会话导航',
    'a11y.closeSidebar': '关闭侧栏', 'a11y.openSidebar': '打开会话侧栏',
    'a11y.commandBar': '智能体与技能命令栏', 'a11y.inputQuestion': '输入问题',
    'a11y.sendMessage': '发送消息', 'a11y.closeSettings': '关闭设置',
    'loading.restore': '正在恢复文昌智脑', 'common.reload': '重新加载',
    'common.connecting': '连接中', 'common.viewAll': '查看全部', 'common.checkingConnection': '正在检查连接',
    'common.available': '可用', 'common.error': '异常', 'common.checking': '检查中',
    'common.notCompleted': '未完成', 'common.again': '再次检查',
    'message.actions': '消息操作', 'message.copy': '复制', 'message.edit': '编辑',
    'message.copied': '已复制', 'message.copyFailed': '复制失败，请手动选择文本',
    'message.editLoaded': '原问题已载入输入框，修改后可重新发送', 'message.waitForReply': '请等待当前回答完成后再编辑',
    'message.versions': '问题版本', 'message.previousVersion': '上一个版本', 'message.nextVersion': '下一个版本',
    'message.editQuestion': '编辑当前问题', 'message.sendEdited': '发送新版本', 'message.emptyEdit': '问题内容不能为空',
    'message.editUnavailable': '请等待消息保存后再编辑', 'common.cancel': '取消', 'artifact.download': '下载文件',
    'sidebar.newChat': '新对话', 'sidebar.noHistory': '还没有历史对话',
    'sidebar.historyHint': '发送第一条消息后会自动保存',
    'settings.title': '模型与设置', 'model.readingStatus': '正在读取模型状态',
    'hero.line1': '从海岸出发', 'hero.line2': '向星辰提问',
    'hero.description': '连接文昌知识、实时信息与智能模型，探索航天、生态、文化和城市生活。',
    'knowledge.connecting': '知识库连接中', 'model.connecting': '模型连接中',
    'welcome.title': '想了解文昌的哪一面？',
    'welcome.description': '可以从航天发射、滨海生态、侨乡文化或研学路线开始。',
    'suggestion.overview': '一分钟认识文昌', 'suggestion.overviewMeta': '城市概览',
    'suggestion.overviewPrompt': '请用一分钟介绍文昌的航天、生态与文化特色。',
    'suggestion.tour': '设计一日研学路线', 'suggestion.tourMeta': '路线规划',
    'suggestion.tourPrompt': '请设计一条包含航天、滨海生态和历史文化的文昌一日研学路线。',
    'suggestion.space': '查询近期航天动态', 'suggestion.spaceMeta': '实时信息',
    'suggestion.spacePrompt': '文昌近期有什么重要航天发射？请联网核验并说明观看注意事项。',
    'chat.thinking': '正在思考', 'command.selectAgent': '选择智能体',
    'command.selectAgentHelp': '选择一个专业智能体，让它按照对应领域和工作方式完成任务',
    'command.useSkill': '使用技能', 'command.useSkillHelp': '直接调用研究、检索、生成文件、研学规划等任务能力',
    'composer.placeholder': '问文昌智脑任何问题...', 'composer.keyboardHint': 'Enter 发送 · Shift + Enter 换行',
    'composer.disclaimer': '回答由知识检索与智能模型生成，重要信息请结合来源核验。',
    'language.title': '系统语言', 'language.help': '切换首页与系统界面的显示语言',
    'settings.provider': '服务商', 'settings.baseUrl': 'API Base URL', 'settings.modelName': '模型名称',
    'settings.thinkingMode': '思考模式', 'settings.thinkingHelp': '适用于支持 reasoning 的模型',
    'settings.editApiKey': '修改 API Key', 'settings.newApiKey': '新的 API Key',
    'settings.apiKeyPlaceholder': '输入新的 API Key', 'settings.testConnection': '测试连接',
    'settings.save': '保存并切换', 'settings.restoreDefault': '恢复服务端默认配置',
    'tools.title': '工具与服务', 'tools.subtitle': '文昌公共资源服务',
    'tools.publicService': '公共服务查询', 'tools.township': '乡镇资料查询', 'tools.studyTour': '研学地点查询',
    'diagnostics.title': '系统状态', 'diagnostics.help': '检查智能体完成任务所需的各项能力',
    'diagnostics.run': '运行智能体自检', 'diagnostics.model': '模型连接', 'diagnostics.rag': '知识库',
    'diagnostics.webSearch': '联网搜索', 'diagnostics.officialSearch': '权威检索',
    'diagnostics.mcp': 'MCP 服务', 'diagnostics.word': 'Word 生成', 'diagnostics.dataExport': '数据导出',
    'diagnostics.waiting': '等待检查', 'privacy.title': '数据与隐私',
    'privacy.description': '会话保存在本机 H2 数据库。真实 API Key 仅由后端读取，浏览器只接收脱敏结果。',
    'palette.agentTitle': '选择智能体', 'palette.skillTitle': '选择技能',
    'palette.keyboardHint': 'Esc 关闭 · 方向键选择 · Enter 确认', 'palette.empty': '没有匹配的能力',
    'palette.viewDetails': '查看说明', 'palette.output': '输出：{value}',
    'group.research': '研究与检索', 'group.files': '任务与文件', 'group.planning': '规划与服务',
    'group.other': '其他能力', 'context.availableSkills': '可用技能：{value}',
    'context.agentReady': '已加载智能体能力', 'context.viewCapabilities': '查看能力',
    'context.viewDetails': '查看说明', 'context.switch': '切换', 'context.useSkill': '/ 使用技能',
    'context.requiredInput': '需要输入：{value}',
    'history.today': '今天', 'history.yesterday': '昨天', 'history.recent': '最近 7 天', 'history.older': '更早',
    'history.rename': '重命名', 'history.delete': '删除',
    'model.notConfigured': '模型未配置', 'model.openSettings': '进入模型设置',
    'model.current': '当前模型', 'model.configureHint': '进入模型设置完成配置',
    'knowledge.notReady': '知识库未就绪', 'knowledge.failed': '知识库连接失败',
    'knowledge.summary': '{files} 份资料 · {chunks} 个分块', 'model.statusUnavailable': '模型状态不可用',
    'tools.connected': '已连接 · {count} 个可用工具', 'tools.disconnected': '服务未连接',
    'tools.statusUnavailable': '服务状态不可用',
    'detail.agentEyebrow': '智能体能力', 'detail.closeAgent': '关闭智能体详情',
    'detail.capabilities': '能做什么', 'detail.inputs': '适合输入', 'detail.skills': '可用技能',
    'detail.tools': '可调用工具', 'detail.artifacts': '可以生成的文件', 'detail.workflow': '典型工作过程',
    'detail.approval': '人工确认', 'detail.examples': '示例任务', 'detail.start': '开始使用',
    'detail.skillEyebrow': '技能说明', 'detail.closeSkill': '关闭技能说明', 'detail.output': '任务输出',
    'detail.execution': '执行方式', 'detail.toolsUsed': '使用工具', 'detail.knowledge': '适用知识',
    'detail.confirmation': '确认方式', 'detail.useSkill': '使用此技能',
    'approval.ready': '准备执行', 'approval.title': '需要你的确认', 'approval.close': '关闭确认窗口',
    'approval.operation': '操作内容', 'approval.scope': '影响范围', 'approval.cancel': '取消', 'approval.confirm': '确认执行'
  };

  const translations = {
    'zh-CN': zh,
    en: {
      ...zh,
      'meta.title': 'Wenchang Brain · Ask from the coast to the stars',
      'meta.description': 'Wenchang Brain connects local knowledge, real-time information, and intelligent models.',
      'a11y.skipToComposer': 'Skip to question box', 'a11y.conversationNav': 'Conversation navigation',
      'a11y.closeSidebar': 'Close sidebar', 'a11y.openSidebar': 'Open conversation sidebar',
      'a11y.commandBar': 'Agent and skill command bar', 'a11y.inputQuestion': 'Enter a question',
      'a11y.sendMessage': 'Send message', 'a11y.closeSettings': 'Close settings',
      'loading.restore': 'Restoring Wenchang Brain', 'common.reload': 'Reload', 'common.connecting': 'Connecting',
      'common.viewAll': 'View all', 'common.checkingConnection': 'Checking connection',
      'common.available': 'Available', 'common.error': 'Error', 'common.checking': 'Checking',
      'common.notCompleted': 'Not completed', 'common.again': 'Check again',
      'message.actions': 'Message actions', 'message.copy': 'Copy', 'message.edit': 'Edit',
      'message.copied': 'Copied', 'message.copyFailed': 'Copy failed. Please select the text manually.',
      'message.editLoaded': 'The question is in the composer. Edit it and send again.', 'message.waitForReply': 'Wait for the current response before editing.',
      'message.versions': 'Question versions', 'message.previousVersion': 'Previous version', 'message.nextVersion': 'Next version',
      'message.editQuestion': 'Edit this question', 'message.sendEdited': 'Send new version', 'message.emptyEdit': 'The question cannot be empty.',
      'message.editUnavailable': 'Wait until the message is saved before editing.', 'common.cancel': 'Cancel', 'artifact.download': 'Download file',
      'sidebar.newChat': 'New chat', 'sidebar.noHistory': 'No conversations yet',
      'sidebar.historyHint': 'Your first message will be saved automatically',
      'settings.title': 'Model & Settings', 'model.readingStatus': 'Reading model status',
      'hero.line1': 'Start from the coast', 'hero.line2': 'Ask toward the stars',
      'hero.description': 'Connect Wenchang knowledge, real-time information, and intelligent models to explore spaceflight, ecology, culture, and city life.',
      'knowledge.connecting': 'Connecting knowledge base', 'model.connecting': 'Connecting model',
      'welcome.title': 'What would you like to discover about Wenchang?',
      'welcome.description': 'Start with space launches, coastal ecology, overseas Chinese culture, or study-tour routes.',
      'suggestion.overview': 'Wenchang in one minute', 'suggestion.overviewMeta': 'City overview',
      'suggestion.overviewPrompt': 'Introduce Wenchang’s spaceflight, ecology, and culture in one minute.',
      'suggestion.tour': 'Plan a one-day study tour', 'suggestion.tourMeta': 'Route planning',
      'suggestion.tourPrompt': 'Design a one-day Wenchang study tour covering spaceflight, coastal ecology, and local history.',
      'suggestion.space': 'Recent spaceflight updates', 'suggestion.spaceMeta': 'Live information',
      'suggestion.spacePrompt': 'What important space launches are coming up in Wenchang? Verify online and explain viewing precautions.',
      'chat.thinking': 'Thinking', 'command.selectAgent': 'Choose an agent',
      'command.selectAgentHelp': 'Choose a specialist agent for the right domain and way of working',
      'command.useSkill': 'Use a skill', 'command.useSkillHelp': 'Start research, search, file generation, or study-tour planning directly',
      'composer.placeholder': 'Ask Wenchang Brain anything...', 'composer.keyboardHint': 'Enter to send · Shift + Enter for a new line',
      'composer.disclaimer': 'Answers are generated from knowledge retrieval and AI. Verify important information against the cited sources.',
      'language.title': 'System language', 'language.help': 'Change the display language of the home page and system interface',
      'settings.provider': 'Provider', 'settings.baseUrl': 'API Base URL', 'settings.modelName': 'Model name',
      'settings.thinkingMode': 'Reasoning mode', 'settings.thinkingHelp': 'For models that support reasoning',
      'settings.editApiKey': 'Change API Key', 'settings.newApiKey': 'New API Key',
      'settings.apiKeyPlaceholder': 'Enter a new API Key', 'settings.testConnection': 'Test connection',
      'settings.save': 'Save and switch', 'settings.restoreDefault': 'Restore server defaults',
      'tools.title': 'Tools & Services', 'tools.subtitle': 'Wenchang public resource services',
      'tools.publicService': 'Public services', 'tools.township': 'Township profiles', 'tools.studyTour': 'Study-tour places',
      'diagnostics.title': 'System status', 'diagnostics.help': 'Check the capabilities agents need to complete tasks',
      'diagnostics.run': 'Run agent diagnostics', 'diagnostics.model': 'Model connection', 'diagnostics.rag': 'Knowledge base',
      'diagnostics.webSearch': 'Web search', 'diagnostics.officialSearch': 'Official search',
      'diagnostics.mcp': 'MCP service', 'diagnostics.word': 'Word generation', 'diagnostics.dataExport': 'Data export',
      'diagnostics.waiting': 'Waiting', 'privacy.title': 'Data & Privacy',
      'privacy.description': 'Conversations are stored in the local H2 database. Real API keys are read only by the backend; the browser receives masked status only.',
      'palette.agentTitle': 'Choose an agent', 'palette.skillTitle': 'Choose a skill',
      'palette.keyboardHint': 'Esc to close · Arrow keys to select · Enter to confirm', 'palette.empty': 'No matching capability',
      'palette.viewDetails': 'View details', 'palette.output': 'Output: {value}',
      'group.research': 'Research & Search', 'group.files': 'Tasks & Files', 'group.planning': 'Planning & Services',
      'group.other': 'Other capabilities', 'context.availableSkills': 'Available skills: {value}',
      'context.agentReady': 'Agent capabilities loaded', 'context.viewCapabilities': 'View capabilities',
      'context.viewDetails': 'View details', 'context.switch': 'Switch', 'context.useSkill': '/ Use skill',
      'context.requiredInput': 'Required input: {value}',
      'history.today': 'Today', 'history.yesterday': 'Yesterday', 'history.recent': 'Last 7 days', 'history.older': 'Earlier',
      'history.rename': 'Rename', 'history.delete': 'Delete',
      'model.notConfigured': 'Model not configured', 'model.openSettings': 'Open model settings',
      'model.current': 'Current model', 'model.configureHint': 'Open model settings to finish configuration',
      'knowledge.notReady': 'Knowledge base not ready', 'knowledge.failed': 'Knowledge base connection failed',
      'knowledge.summary': '{files} files · {chunks} chunks', 'model.statusUnavailable': 'Model status unavailable',
      'tools.connected': 'Connected · {count} tools available', 'tools.disconnected': 'Service disconnected',
      'tools.statusUnavailable': 'Service status unavailable',
      'detail.agentEyebrow': 'Agent capabilities', 'detail.closeAgent': 'Close agent details',
      'detail.capabilities': 'What it can do', 'detail.inputs': 'Suitable input', 'detail.skills': 'Available skills',
      'detail.tools': 'Callable tools', 'detail.artifacts': 'Files it can create', 'detail.workflow': 'Typical workflow',
      'detail.approval': 'Human approval', 'detail.examples': 'Example tasks', 'detail.start': 'Start using',
      'detail.skillEyebrow': 'Skill details', 'detail.closeSkill': 'Close skill details', 'detail.output': 'Task output',
      'detail.execution': 'Execution method', 'detail.toolsUsed': 'Tools used', 'detail.knowledge': 'Applicable knowledge',
      'detail.confirmation': 'Confirmation', 'detail.useSkill': 'Use this skill',
      'approval.ready': 'Ready to execute', 'approval.title': 'Your confirmation is required', 'approval.close': 'Close confirmation',
      'approval.operation': 'Operation', 'approval.scope': 'Impact scope', 'approval.cancel': 'Cancel', 'approval.confirm': 'Confirm execution'
    },
    id: {
      ...zh,
      'meta.title': 'Wenchang Brain · Bertanya dari pesisir menuju bintang',
      'meta.description': 'Wenchang Brain menghubungkan pengetahuan lokal, informasi waktu nyata, dan model cerdas.',
      'a11y.skipToComposer': 'Lewati ke kotak pertanyaan', 'a11y.conversationNav': 'Navigasi percakapan',
      'a11y.closeSidebar': 'Tutup bilah samping', 'a11y.openSidebar': 'Buka bilah percakapan',
      'a11y.commandBar': 'Bilah perintah agen dan keterampilan', 'a11y.inputQuestion': 'Masukkan pertanyaan',
      'a11y.sendMessage': 'Kirim pesan', 'a11y.closeSettings': 'Tutup pengaturan',
      'loading.restore': 'Memulihkan Wenchang Brain', 'common.reload': 'Muat ulang', 'common.connecting': 'Menghubungkan',
      'common.viewAll': 'Lihat semua', 'common.checkingConnection': 'Memeriksa koneksi',
      'common.available': 'Tersedia', 'common.error': 'Bermasalah', 'common.checking': 'Memeriksa',
      'common.notCompleted': 'Belum selesai', 'common.again': 'Periksa lagi',
      'message.actions': 'Tindakan pesan', 'message.copy': 'Salin', 'message.edit': 'Edit',
      'message.copied': 'Disalin', 'message.copyFailed': 'Gagal menyalin. Pilih teks secara manual.',
      'message.editLoaded': 'Pertanyaan dimuat ke kolom input. Edit lalu kirim kembali.', 'message.waitForReply': 'Tunggu jawaban saat ini selesai sebelum mengedit.',
      'message.versions': 'Versi pertanyaan', 'message.previousVersion': 'Versi sebelumnya', 'message.nextVersion': 'Versi berikutnya',
      'message.editQuestion': 'Edit pertanyaan ini', 'message.sendEdited': 'Kirim versi baru', 'message.emptyEdit': 'Pertanyaan tidak boleh kosong.',
      'message.editUnavailable': 'Tunggu hingga pesan tersimpan sebelum mengedit.', 'common.cancel': 'Batal', 'artifact.download': 'Unduh file',
      'sidebar.newChat': 'Percakapan baru', 'sidebar.noHistory': 'Belum ada riwayat percakapan',
      'sidebar.historyHint': 'Pesan pertama akan disimpan secara otomatis',
      'settings.title': 'Model & Pengaturan', 'model.readingStatus': 'Membaca status model',
      'hero.line1': 'Berangkat dari pesisir', 'hero.line2': 'Bertanya menuju bintang',
      'hero.description': 'Hubungkan pengetahuan Wenchang, informasi waktu nyata, dan model cerdas untuk menjelajahi antariksa, ekologi, budaya, dan kehidupan kota.',
      'knowledge.connecting': 'Menghubungkan basis pengetahuan', 'model.connecting': 'Menghubungkan model',
      'welcome.title': 'Sisi Wenchang mana yang ingin Anda jelajahi?',
      'welcome.description': 'Mulai dari peluncuran antariksa, ekologi pesisir, budaya perantauan, atau rute wisata edukasi.',
      'suggestion.overview': 'Wenchang dalam satu menit', 'suggestion.overviewMeta': 'Gambaran kota',
      'suggestion.overviewPrompt': 'Perkenalkan antariksa, ekologi, dan budaya Wenchang dalam satu menit.',
      'suggestion.tour': 'Rancang wisata edukasi sehari', 'suggestion.tourMeta': 'Perencanaan rute',
      'suggestion.tourPrompt': 'Rancang wisata edukasi sehari di Wenchang yang mencakup antariksa, ekologi pesisir, dan sejarah lokal.',
      'suggestion.space': 'Kabar antariksa terbaru', 'suggestion.spaceMeta': 'Informasi langsung',
      'suggestion.spacePrompt': 'Peluncuran antariksa penting apa yang akan berlangsung di Wenchang? Verifikasi secara daring dan jelaskan panduan menontonnya.',
      'chat.thinking': 'Sedang berpikir', 'command.selectAgent': 'Pilih agen',
      'command.selectAgentHelp': 'Pilih agen spesialis sesuai bidang dan cara kerja tugas',
      'command.useSkill': 'Gunakan keterampilan', 'command.useSkillHelp': 'Mulai riset, pencarian, pembuatan file, atau perencanaan wisata edukasi',
      'composer.placeholder': 'Tanyakan apa saja kepada Wenchang Brain...', 'composer.keyboardHint': 'Enter untuk mengirim · Shift + Enter untuk baris baru',
      'composer.disclaimer': 'Jawaban dibuat dari pencarian pengetahuan dan AI. Verifikasi informasi penting melalui sumber yang dicantumkan.',
      'language.title': 'Bahasa sistem', 'language.help': 'Ubah bahasa tampilan halaman utama dan antarmuka sistem',
      'settings.provider': 'Penyedia', 'settings.baseUrl': 'URL dasar API', 'settings.modelName': 'Nama model',
      'settings.thinkingMode': 'Mode penalaran', 'settings.thinkingHelp': 'Untuk model yang mendukung penalaran',
      'settings.editApiKey': 'Ubah API Key', 'settings.newApiKey': 'API Key baru',
      'settings.apiKeyPlaceholder': 'Masukkan API Key baru', 'settings.testConnection': 'Uji koneksi',
      'settings.save': 'Simpan dan gunakan', 'settings.restoreDefault': 'Pulihkan bawaan server',
      'tools.title': 'Alat & Layanan', 'tools.subtitle': 'Layanan sumber daya publik Wenchang',
      'tools.publicService': 'Layanan publik', 'tools.township': 'Profil kecamatan', 'tools.studyTour': 'Lokasi wisata edukasi',
      'diagnostics.title': 'Status sistem', 'diagnostics.help': 'Periksa kemampuan yang dibutuhkan agen untuk menyelesaikan tugas',
      'diagnostics.run': 'Jalankan diagnostik agen', 'diagnostics.model': 'Koneksi model', 'diagnostics.rag': 'Basis pengetahuan',
      'diagnostics.webSearch': 'Pencarian web', 'diagnostics.officialSearch': 'Pencarian resmi',
      'diagnostics.mcp': 'Layanan MCP', 'diagnostics.word': 'Pembuatan Word', 'diagnostics.dataExport': 'Ekspor data',
      'diagnostics.waiting': 'Menunggu', 'privacy.title': 'Data & Privasi',
      'privacy.description': 'Percakapan disimpan di basis data H2 lokal. API Key asli hanya dibaca backend; browser hanya menerima status tersamarkan.',
      'palette.agentTitle': 'Pilih agen', 'palette.skillTitle': 'Pilih keterampilan',
      'palette.keyboardHint': 'Esc untuk tutup · Tombol panah untuk memilih · Enter untuk konfirmasi', 'palette.empty': 'Tidak ada kemampuan yang cocok',
      'palette.viewDetails': 'Lihat penjelasan', 'palette.output': 'Keluaran: {value}',
      'group.research': 'Riset & Pencarian', 'group.files': 'Tugas & File', 'group.planning': 'Perencanaan & Layanan',
      'group.other': 'Kemampuan lain', 'context.availableSkills': 'Keterampilan tersedia: {value}',
      'context.agentReady': 'Kemampuan agen telah dimuat', 'context.viewCapabilities': 'Lihat kemampuan',
      'context.viewDetails': 'Lihat penjelasan', 'context.switch': 'Ganti', 'context.useSkill': '/ Gunakan keterampilan',
      'context.requiredInput': 'Masukan yang diperlukan: {value}',
      'history.today': 'Hari ini', 'history.yesterday': 'Kemarin', 'history.recent': '7 hari terakhir', 'history.older': 'Sebelumnya',
      'history.rename': 'Ganti nama', 'history.delete': 'Hapus',
      'model.notConfigured': 'Model belum dikonfigurasi', 'model.openSettings': 'Buka pengaturan model',
      'model.current': 'Model saat ini', 'model.configureHint': 'Buka pengaturan model untuk menyelesaikan konfigurasi',
      'knowledge.notReady': 'Basis pengetahuan belum siap', 'knowledge.failed': 'Koneksi basis pengetahuan gagal',
      'knowledge.summary': '{files} file · {chunks} bagian', 'model.statusUnavailable': 'Status model tidak tersedia',
      'tools.connected': 'Terhubung · {count} alat tersedia', 'tools.disconnected': 'Layanan tidak terhubung',
      'tools.statusUnavailable': 'Status layanan tidak tersedia',
      'detail.agentEyebrow': 'Kemampuan agen', 'detail.closeAgent': 'Tutup detail agen',
      'detail.capabilities': 'Yang dapat dilakukan', 'detail.inputs': 'Masukan yang sesuai', 'detail.skills': 'Keterampilan tersedia',
      'detail.tools': 'Alat yang dapat dipanggil', 'detail.artifacts': 'File yang dapat dibuat', 'detail.workflow': 'Alur kerja umum',
      'detail.approval': 'Persetujuan manusia', 'detail.examples': 'Contoh tugas', 'detail.start': 'Mulai gunakan',
      'detail.skillEyebrow': 'Penjelasan keterampilan', 'detail.closeSkill': 'Tutup penjelasan keterampilan', 'detail.output': 'Keluaran tugas',
      'detail.execution': 'Cara pelaksanaan', 'detail.toolsUsed': 'Alat yang digunakan', 'detail.knowledge': 'Pengetahuan terkait',
      'detail.confirmation': 'Cara konfirmasi', 'detail.useSkill': 'Gunakan keterampilan ini',
      'approval.ready': 'Siap dijalankan', 'approval.title': 'Konfirmasi Anda diperlukan', 'approval.close': 'Tutup konfirmasi',
      'approval.operation': 'Operasi', 'approval.scope': 'Cakupan dampak', 'approval.cancel': 'Batal', 'approval.confirm': 'Konfirmasi pelaksanaan'
    },
    ar: {
      ...zh,
      'meta.title': 'عقل ونشانغ · اسأل من الساحل إلى النجوم',
      'meta.description': 'يربط عقل ونشانغ المعرفة المحلية والمعلومات الآنية والنماذج الذكية.',
      'a11y.skipToComposer': 'الانتقال إلى مربع السؤال', 'a11y.conversationNav': 'التنقل بين المحادثات',
      'a11y.closeSidebar': 'إغلاق الشريط الجانبي', 'a11y.openSidebar': 'فتح شريط المحادثات',
      'a11y.commandBar': 'شريط أوامر الوكلاء والمهارات', 'a11y.inputQuestion': 'أدخل سؤالاً',
      'a11y.sendMessage': 'إرسال الرسالة', 'a11y.closeSettings': 'إغلاق الإعدادات',
      'loading.restore': 'جارٍ استعادة عقل ونشانغ', 'common.reload': 'إعادة التحميل', 'common.connecting': 'جارٍ الاتصال',
      'common.viewAll': 'عرض الكل', 'common.checkingConnection': 'جارٍ فحص الاتصال',
      'common.available': 'متاح', 'common.error': 'خلل', 'common.checking': 'جارٍ الفحص',
      'common.notCompleted': 'غير مكتمل', 'common.again': 'فحص مرة أخرى',
      'message.actions': 'إجراءات الرسالة', 'message.copy': 'نسخ', 'message.edit': 'تعديل',
      'message.copied': 'تم النسخ', 'message.copyFailed': 'تعذر النسخ. حدّد النص يدوياً.',
      'message.editLoaded': 'تم تحميل السؤال في مربع الإدخال. عدّله ثم أرسله مجدداً.', 'message.waitForReply': 'انتظر اكتمال الرد الحالي قبل التعديل.',
      'message.versions': 'إصدارات السؤال', 'message.previousVersion': 'الإصدار السابق', 'message.nextVersion': 'الإصدار التالي',
      'message.editQuestion': 'تعديل هذا السؤال', 'message.sendEdited': 'إرسال إصدار جديد', 'message.emptyEdit': 'لا يمكن أن يكون السؤال فارغاً.',
      'message.editUnavailable': 'انتظر حتى يتم حفظ الرسالة قبل تعديلها.', 'common.cancel': 'إلغاء', 'artifact.download': 'تنزيل الملف',
      'sidebar.newChat': 'محادثة جديدة', 'sidebar.noHistory': 'لا توجد محادثات بعد',
      'sidebar.historyHint': 'ستُحفظ رسالتك الأولى تلقائياً',
      'settings.title': 'النموذج والإعدادات', 'model.readingStatus': 'جارٍ قراءة حالة النموذج',
      'hero.line1': 'انطلق من الساحل', 'hero.line2': 'واسأل نحو النجوم',
      'hero.description': 'اربط معرفة ونشانغ والمعلومات الآنية والنماذج الذكية لاستكشاف الفضاء والبيئة والثقافة والحياة الحضرية.',
      'knowledge.connecting': 'جارٍ الاتصال بقاعدة المعرفة', 'model.connecting': 'جارٍ الاتصال بالنموذج',
      'welcome.title': 'أي جانب من ونشانغ تريد استكشافه؟',
      'welcome.description': 'ابدأ بإطلاقات الفضاء أو البيئة الساحلية أو ثقافة المغتربين أو مسارات الرحلات التعليمية.',
      'suggestion.overview': 'ونشانغ في دقيقة', 'suggestion.overviewMeta': 'نظرة عامة على المدينة',
      'suggestion.overviewPrompt': 'عرّف بخصائص ونشانغ الفضائية والبيئية والثقافية في دقيقة واحدة.',
      'suggestion.tour': 'تصميم رحلة تعليمية ليوم واحد', 'suggestion.tourMeta': 'تخطيط المسار',
      'suggestion.tourPrompt': 'صمّم رحلة تعليمية ليوم واحد في ونشانغ تشمل الفضاء والبيئة الساحلية والتاريخ المحلي.',
      'suggestion.space': 'آخر أخبار الفضاء', 'suggestion.spaceMeta': 'معلومات آنية',
      'suggestion.spacePrompt': 'ما أهم عمليات الإطلاق الفضائي القادمة في ونشانغ؟ تحقّق عبر الإنترنت واشرح احتياطات المشاهدة.',
      'chat.thinking': 'جارٍ التفكير', 'command.selectAgent': 'اختر وكيلاً',
      'command.selectAgentHelp': 'اختر وكيلاً متخصصاً يناسب المجال وطريقة تنفيذ المهمة',
      'command.useSkill': 'استخدم مهارة', 'command.useSkillHelp': 'ابدأ البحث أو التحقق أو إنشاء الملفات أو تخطيط الرحلات التعليمية مباشرة',
      'composer.placeholder': 'اسأل عقل ونشانغ عن أي شيء...', 'composer.keyboardHint': 'Enter للإرسال · Shift + Enter لسطر جديد',
      'composer.disclaimer': 'تُنشأ الإجابات بالاسترجاع المعرفي والذكاء الاصطناعي. تحقّق من المعلومات المهمة عبر المصادر المذكورة.',
      'language.title': 'لغة النظام', 'language.help': 'غيّر لغة عرض الصفحة الرئيسية وواجهة النظام',
      'settings.provider': 'المزوّد', 'settings.baseUrl': 'عنوان API الأساسي', 'settings.modelName': 'اسم النموذج',
      'settings.thinkingMode': 'وضع الاستدلال', 'settings.thinkingHelp': 'للنماذج التي تدعم الاستدلال',
      'settings.editApiKey': 'تغيير مفتاح API', 'settings.newApiKey': 'مفتاح API جديد',
      'settings.apiKeyPlaceholder': 'أدخل مفتاح API جديداً', 'settings.testConnection': 'اختبار الاتصال',
      'settings.save': 'حفظ وتبديل', 'settings.restoreDefault': 'استعادة إعدادات الخادم الافتراضية',
      'tools.title': 'الأدوات والخدمات', 'tools.subtitle': 'خدمات الموارد العامة في ونشانغ',
      'tools.publicService': 'الخدمات العامة', 'tools.township': 'ملفات البلدات', 'tools.studyTour': 'أماكن الرحلات التعليمية',
      'diagnostics.title': 'حالة النظام', 'diagnostics.help': 'افحص القدرات التي يحتاجها الوكلاء لإتمام المهام',
      'diagnostics.run': 'تشغيل فحص الوكيل', 'diagnostics.model': 'اتصال النموذج', 'diagnostics.rag': 'قاعدة المعرفة',
      'diagnostics.webSearch': 'بحث الويب', 'diagnostics.officialSearch': 'البحث الرسمي',
      'diagnostics.mcp': 'خدمة MCP', 'diagnostics.word': 'إنشاء Word', 'diagnostics.dataExport': 'تصدير البيانات',
      'diagnostics.waiting': 'بانتظار الفحص', 'privacy.title': 'البيانات والخصوصية',
      'privacy.description': 'تُحفظ المحادثات في قاعدة بيانات H2 المحلية. يقرأ الخادم فقط مفاتيح API الحقيقية، ويتلقى المتصفح حالة مخفية.',
      'palette.agentTitle': 'اختر وكيلاً', 'palette.skillTitle': 'اختر مهارة',
      'palette.keyboardHint': 'Esc للإغلاق · الأسهم للاختيار · Enter للتأكيد', 'palette.empty': 'لا توجد قدرة مطابقة',
      'palette.viewDetails': 'عرض الشرح', 'palette.output': 'المخرجات: {value}',
      'group.research': 'البحث والتحقق', 'group.files': 'المهام والملفات', 'group.planning': 'التخطيط والخدمات',
      'group.other': 'قدرات أخرى', 'context.availableSkills': 'المهارات المتاحة: {value}',
      'context.agentReady': 'تم تحميل قدرات الوكيل', 'context.viewCapabilities': 'عرض القدرات',
      'context.viewDetails': 'عرض الشرح', 'context.switch': 'تبديل', 'context.useSkill': '/ استخدام مهارة',
      'context.requiredInput': 'المدخلات المطلوبة: {value}',
      'history.today': 'اليوم', 'history.yesterday': 'أمس', 'history.recent': 'آخر 7 أيام', 'history.older': 'أقدم',
      'history.rename': 'إعادة تسمية', 'history.delete': 'حذف',
      'model.notConfigured': 'النموذج غير مهيأ', 'model.openSettings': 'فتح إعدادات النموذج',
      'model.current': 'النموذج الحالي', 'model.configureHint': 'افتح إعدادات النموذج لإكمال التهيئة',
      'knowledge.notReady': 'قاعدة المعرفة غير جاهزة', 'knowledge.failed': 'فشل الاتصال بقاعدة المعرفة',
      'knowledge.summary': '{files} ملف · {chunks} مقطع', 'model.statusUnavailable': 'حالة النموذج غير متاحة',
      'tools.connected': 'متصل · {count} أدوات متاحة', 'tools.disconnected': 'الخدمة غير متصلة',
      'tools.statusUnavailable': 'حالة الخدمة غير متاحة',
      'detail.agentEyebrow': 'قدرات الوكيل', 'detail.closeAgent': 'إغلاق تفاصيل الوكيل',
      'detail.capabilities': 'ما الذي يمكنه فعله', 'detail.inputs': 'المدخلات المناسبة', 'detail.skills': 'المهارات المتاحة',
      'detail.tools': 'الأدوات القابلة للاستدعاء', 'detail.artifacts': 'الملفات التي يمكن إنشاؤها', 'detail.workflow': 'سير العمل المعتاد',
      'detail.approval': 'الموافقة البشرية', 'detail.examples': 'مهام نموذجية', 'detail.start': 'بدء الاستخدام',
      'detail.skillEyebrow': 'شرح المهارة', 'detail.closeSkill': 'إغلاق شرح المهارة', 'detail.output': 'مخرجات المهمة',
      'detail.execution': 'طريقة التنفيذ', 'detail.toolsUsed': 'الأدوات المستخدمة', 'detail.knowledge': 'المعرفة المناسبة',
      'detail.confirmation': 'طريقة التأكيد', 'detail.useSkill': 'استخدام هذه المهارة',
      'approval.ready': 'جاهز للتنفيذ', 'approval.title': 'موافقتك مطلوبة', 'approval.close': 'إغلاق التأكيد',
      'approval.operation': 'العملية', 'approval.scope': 'نطاق التأثير', 'approval.cancel': 'إلغاء', 'approval.confirm': 'تأكيد التنفيذ'
    },
    pt: {
      ...zh,
      'meta.title': 'Wenchang Brain · Pergunte da costa às estrelas',
      'meta.description': 'O Wenchang Brain conecta conhecimento local, informação em tempo real e modelos inteligentes.',
      'a11y.skipToComposer': 'Ir para a caixa de pergunta', 'a11y.conversationNav': 'Navegação de conversas',
      'a11y.closeSidebar': 'Fechar barra lateral', 'a11y.openSidebar': 'Abrir barra de conversas',
      'a11y.commandBar': 'Barra de agentes e competências', 'a11y.inputQuestion': 'Introduza uma pergunta',
      'a11y.sendMessage': 'Enviar mensagem', 'a11y.closeSettings': 'Fechar definições',
      'loading.restore': 'A restaurar o Wenchang Brain', 'common.reload': 'Recarregar', 'common.connecting': 'A ligar',
      'common.viewAll': 'Ver tudo', 'common.checkingConnection': 'A verificar ligação',
      'common.available': 'Disponível', 'common.error': 'Anomalia', 'common.checking': 'A verificar',
      'common.notCompleted': 'Não concluído', 'common.again': 'Verificar novamente',
      'message.actions': 'Ações da mensagem', 'message.copy': 'Copiar', 'message.edit': 'Editar',
      'message.copied': 'Copiado', 'message.copyFailed': 'Falha ao copiar. Selecione o texto manualmente.',
      'message.editLoaded': 'A pergunta foi carregada na caixa. Edite e envie novamente.', 'message.waitForReply': 'Aguarde a resposta atual terminar antes de editar.',
      'message.versions': 'Versões da pergunta', 'message.previousVersion': 'Versão anterior', 'message.nextVersion': 'Versão seguinte',
      'message.editQuestion': 'Editar esta pergunta', 'message.sendEdited': 'Enviar nova versão', 'message.emptyEdit': 'A pergunta não pode estar vazia.',
      'message.editUnavailable': 'Aguarde até a mensagem ser guardada.', 'common.cancel': 'Cancelar', 'artifact.download': 'Transferir ficheiro',
      'sidebar.newChat': 'Nova conversa', 'sidebar.noHistory': 'Ainda não há conversas',
      'sidebar.historyHint': 'A primeira mensagem será guardada automaticamente',
      'settings.title': 'Modelo e definições', 'model.readingStatus': 'A ler o estado do modelo',
      'hero.line1': 'Parta da costa', 'hero.line2': 'Pergunte às estrelas',
      'hero.description': 'Ligue o conhecimento de Wenchang, informação em tempo real e modelos inteligentes para explorar espaço, ecologia, cultura e vida urbana.',
      'knowledge.connecting': 'A ligar à base de conhecimento', 'model.connecting': 'A ligar ao modelo',
      'welcome.title': 'Que lado de Wenchang gostaria de conhecer?',
      'welcome.description': 'Comece por lançamentos espaciais, ecologia costeira, cultura da diáspora ou roteiros educativos.',
      'suggestion.overview': 'Wenchang num minuto', 'suggestion.overviewMeta': 'Visão geral da cidade',
      'suggestion.overviewPrompt': 'Apresente em um minuto as características espaciais, ecológicas e culturais de Wenchang.',
      'suggestion.tour': 'Planear um roteiro educativo', 'suggestion.tourMeta': 'Planeamento de rota',
      'suggestion.tourPrompt': 'Crie um roteiro educativo de um dia em Wenchang com espaço, ecologia costeira e história local.',
      'suggestion.space': 'Atualizações espaciais recentes', 'suggestion.spaceMeta': 'Informação em tempo real',
      'suggestion.spacePrompt': 'Quais são os próximos lançamentos espaciais importantes em Wenchang? Verifique online e explique os cuidados para assistir.',
      'chat.thinking': 'A pensar', 'command.selectAgent': 'Escolher agente',
      'command.selectAgentHelp': 'Escolha um agente especialista para o domínio e método de trabalho adequados',
      'command.useSkill': 'Usar competência', 'command.useSkillHelp': 'Inicie pesquisa, busca, geração de ficheiros ou planeamento educativo',
      'composer.placeholder': 'Pergunte qualquer coisa ao Wenchang Brain...', 'composer.keyboardHint': 'Enter para enviar · Shift + Enter para nova linha',
      'composer.disclaimer': 'As respostas são geradas por recuperação de conhecimento e IA. Confirme informações importantes nas fontes citadas.',
      'language.title': 'Idioma do sistema', 'language.help': 'Altere o idioma da página inicial e da interface do sistema',
      'settings.provider': 'Fornecedor', 'settings.baseUrl': 'URL base da API', 'settings.modelName': 'Nome do modelo',
      'settings.thinkingMode': 'Modo de raciocínio', 'settings.thinkingHelp': 'Para modelos com suporte a raciocínio',
      'settings.editApiKey': 'Alterar API Key', 'settings.newApiKey': 'Nova API Key',
      'settings.apiKeyPlaceholder': 'Introduza uma nova API Key', 'settings.testConnection': 'Testar ligação',
      'settings.save': 'Guardar e mudar', 'settings.restoreDefault': 'Repor predefinições do servidor',
      'tools.title': 'Ferramentas e serviços', 'tools.subtitle': 'Serviços de recursos públicos de Wenchang',
      'tools.publicService': 'Serviços públicos', 'tools.township': 'Perfis das localidades', 'tools.studyTour': 'Locais educativos',
      'diagnostics.title': 'Estado do sistema', 'diagnostics.help': 'Verifique as capacidades necessárias para os agentes concluírem tarefas',
      'diagnostics.run': 'Executar diagnóstico do agente', 'diagnostics.model': 'Ligação do modelo', 'diagnostics.rag': 'Base de conhecimento',
      'diagnostics.webSearch': 'Pesquisa web', 'diagnostics.officialSearch': 'Pesquisa oficial',
      'diagnostics.mcp': 'Serviço MCP', 'diagnostics.word': 'Geração de Word', 'diagnostics.dataExport': 'Exportação de dados',
      'diagnostics.waiting': 'A aguardar', 'privacy.title': 'Dados e privacidade',
      'privacy.description': 'As conversas são guardadas na base H2 local. As API Keys reais são lidas apenas pelo backend; o navegador recebe somente o estado ocultado.',
      'palette.agentTitle': 'Escolher agente', 'palette.skillTitle': 'Escolher competência',
      'palette.keyboardHint': 'Esc para fechar · Setas para selecionar · Enter para confirmar', 'palette.empty': 'Nenhuma capacidade correspondente',
      'palette.viewDetails': 'Ver explicação', 'palette.output': 'Resultado: {value}',
      'group.research': 'Pesquisa e verificação', 'group.files': 'Tarefas e ficheiros', 'group.planning': 'Planeamento e serviços',
      'group.other': 'Outras capacidades', 'context.availableSkills': 'Competências disponíveis: {value}',
      'context.agentReady': 'Capacidades do agente carregadas', 'context.viewCapabilities': 'Ver capacidades',
      'context.viewDetails': 'Ver explicação', 'context.switch': 'Mudar', 'context.useSkill': '/ Usar competência',
      'context.requiredInput': 'Informação necessária: {value}',
      'history.today': 'Hoje', 'history.yesterday': 'Ontem', 'history.recent': 'Últimos 7 dias', 'history.older': 'Anteriores',
      'history.rename': 'Mudar nome', 'history.delete': 'Eliminar',
      'model.notConfigured': 'Modelo não configurado', 'model.openSettings': 'Abrir definições do modelo',
      'model.current': 'Modelo atual', 'model.configureHint': 'Abra as definições do modelo para concluir a configuração',
      'knowledge.notReady': 'Base de conhecimento não está pronta', 'knowledge.failed': 'Falha na ligação à base de conhecimento',
      'knowledge.summary': '{files} ficheiros · {chunks} segmentos', 'model.statusUnavailable': 'Estado do modelo indisponível',
      'tools.connected': 'Ligado · {count} ferramentas disponíveis', 'tools.disconnected': 'Serviço desligado',
      'tools.statusUnavailable': 'Estado do serviço indisponível',
      'detail.agentEyebrow': 'Capacidades do agente', 'detail.closeAgent': 'Fechar detalhes do agente',
      'detail.capabilities': 'O que pode fazer', 'detail.inputs': 'Informação adequada', 'detail.skills': 'Competências disponíveis',
      'detail.tools': 'Ferramentas disponíveis', 'detail.artifacts': 'Ficheiros que pode criar', 'detail.workflow': 'Fluxo de trabalho típico',
      'detail.approval': 'Aprovação humana', 'detail.examples': 'Exemplos de tarefas', 'detail.start': 'Começar a usar',
      'detail.skillEyebrow': 'Explicação da competência', 'detail.closeSkill': 'Fechar explicação da competência', 'detail.output': 'Resultado da tarefa',
      'detail.execution': 'Modo de execução', 'detail.toolsUsed': 'Ferramentas usadas', 'detail.knowledge': 'Conhecimento aplicável',
      'detail.confirmation': 'Forma de confirmação', 'detail.useSkill': 'Usar esta competência',
      'approval.ready': 'Pronto para executar', 'approval.title': 'A sua confirmação é necessária', 'approval.close': 'Fechar confirmação',
      'approval.operation': 'Operação', 'approval.scope': 'Âmbito do impacto', 'approval.cancel': 'Cancelar', 'approval.confirm': 'Confirmar execução'
    }
  };

  function normalizeLanguage(value) {
    const language = String(value || '').trim();
    if (SUPPORTED.includes(language)) return language;
    if (language.toLowerCase().startsWith('zh')) return DEFAULT_LANGUAGE;
    const short = language.toLowerCase().split('-')[0];
    return SUPPORTED.includes(short) ? short : DEFAULT_LANGUAGE;
  }

  function readStoredLanguage() {
    try { return normalizeLanguage(localStorage.getItem(STORAGE_KEY)); }
    catch { return DEFAULT_LANGUAGE; }
  }

  activeLanguage = readStoredLanguage();

  function getLanguage() {
    return activeLanguage;
  }

  function interpolate(value, variables) {
    return Object.entries(variables || {}).reduce(
      (text, entry) => text.replaceAll(`{${entry[0]}}`, String(entry[1])), String(value)
    );
  }

  function t(key, variables, language = getLanguage()) {
    const dictionary = translations[normalizeLanguage(language)] || translations[DEFAULT_LANGUAGE];
    return interpolate(dictionary[key] ?? zh[key] ?? key, variables);
  }

  function apply(root = document) {
    const language = getLanguage();
    document.documentElement.lang = language;
    document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
    root.querySelectorAll('[data-i18n]').forEach((element) => {
      element.textContent = t(element.dataset.i18n, null, language);
    });
    root.querySelectorAll('[data-i18n-placeholder]').forEach((element) => {
      element.placeholder = t(element.dataset.i18nPlaceholder, null, language);
    });
    root.querySelectorAll('[data-i18n-aria-label]').forEach((element) => {
      element.setAttribute('aria-label', t(element.dataset.i18nAriaLabel, null, language));
    });
    root.querySelectorAll('[data-i18n-content]').forEach((element) => {
      element.setAttribute('content', t(element.dataset.i18nContent, null, language));
    });
    root.querySelectorAll('[data-i18n-prompt]').forEach((element) => {
      element.dataset.prompt = t(element.dataset.i18nPrompt, null, language);
    });
    const select = document.getElementById('languageInput');
    if (select) select.value = language;
    document.querySelectorAll('[data-language-value]').forEach((button) => {
      const selected = button.dataset.languageValue === language;
      button.classList.toggle('is-active', selected);
      button.setAttribute('aria-checked', String(selected));
    });
    const live = document.getElementById('languageLive');
    if (live) live.textContent = `✓ ${LANGUAGE_NAMES[language]}`;
    return language;
  }

  function setLanguage(value) {
    const language = normalizeLanguage(value);
    activeLanguage = language;
    try { localStorage.setItem(STORAGE_KEY, language); } catch { /* private browsing */ }
    apply(document);
    window.dispatchEvent(new CustomEvent('wenchang:languagechange', {detail: {language}}));
    return language;
  }

  document.addEventListener('click', (event) => {
    const button = event.target.closest('[data-language-value]');
    if (!button) return;
    setLanguage(button.dataset.languageValue);
  });

  document.addEventListener('change', (event) => {
    if (event.target?.id === 'languageInput') setLanguage(event.target.value);
  });

  window.WenchangI18n = Object.freeze({
    supported: Object.freeze([...SUPPORTED]),
    defaultLanguage: DEFAULT_LANGUAGE,
    getLanguage,
    setLanguage,
    t,
    apply
  });

  apply(document);
})();
