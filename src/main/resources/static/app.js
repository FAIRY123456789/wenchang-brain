const i18n = window.WenchangI18n;
const t = (key, variables) => i18n?.t(key, variables) || key;

const state = {
  conversations: [],
  activeConversationId: localStorage.getItem('wenchang-active-conversation') || null,
  model: null,
  busy: false,
  openMenuId: null,
  appState: 'APP_LOADING',
  hydrated: false,
  initializing: false,
  renderingHistory: false,
  heroTimer: null,
  sidebarTrigger: null,
  settingsTrigger: null,
  agents: [],
  skills: [],
  selectedAgentId: 'wenchang',
  selectedSkillId: null,
  agentSelectionExplicit: false,
  commandState: 'COMMAND_IDLE',
  paletteType: null,
  paletteItems: [],
  paletteIndex: 0,
  paletteOpenedFromInput: false,
  commandTriggerElement: null,
  activeRun: null,
  inlineEditor: null,
  toolCatalog: null,
  detailAgentId: null,
  detailSkillId: null,
  detailReturnFocus: null,
  pendingApproval: null,
  language: i18n?.getLanguage() || 'zh-CN'
};

const $ = (id) => document.getElementById(id);
const APP_BASE_PATH = (() => {
  const pathname = window.location.pathname || '/';
  const marker = '/wenchang-brain';
  return pathname === marker || pathname.startsWith(`${marker}/`) ? marker : '';
})();
const appUrl = (value = '') => {
  const url = String(value || '').trim();
  if (!url || /^https?:\/\//i.test(url)) return url;
  const normalized = url.startsWith('/') ? url : `/${url}`;
  if (APP_BASE_PATH && (normalized === APP_BASE_PATH || normalized.startsWith(`${APP_BASE_PATH}/`))) return normalized;
  return `${APP_BASE_PATH}${normalized}`;
};
const root = document.documentElement;
const main = $('mainContent');
const hero = $('hero');
const sidebar = $('sidebar');
const sidebarOpen = $('sidebarOpen');
const sidebarClose = $('sidebarClose');
const mobileOverlay = $('mobileOverlay');
const history = $('history');
const historyEmpty = $('historyEmpty');
const messages = $('messages');
const form = $('chatForm');
const input = $('messageInput');
const sendButton = $('sendButton');
const newChatButton = $('newChatButton');
const progress = $('progress');
const progressText = $('progressText');
const settingsDrawer = $('settingsDrawer');
const drawerBackdrop = $('drawerBackdrop');
const settingsForm = $('settingsForm');
const apiKeyEditor = $('apiKeyEditor');
const apiKeyInput = $('apiKeyInput');
const testResult = $('testResult');
const toast = $('toast');
const appLoadingText = $('appLoadingText');
const appRetry = $('appRetry');
const commandPalette = $('commandPalette');
const composerSelections = $('composerSelections');
const agentCommandBar = $('agentCommandBar');
const commandIdle = $('commandIdle');
const agentCommandTrigger = $('agentCommandTrigger');
const skillCommandTrigger = $('skillCommandTrigger');
const languageInput = $('languageInput');
const mobileSidebarQuery = window.matchMedia('(max-width: 900px)');
const agentDetailDialog = $('agentDetailDialog');
const skillDetailDialog = $('skillDetailDialog');
const approvalDialog = $('approvalDialog');
let toastTimer;

form.addEventListener('submit', (event) => {
  event.preventDefault();
  sendMessage(input.value);
});
input.addEventListener('keydown', (event) => {
  if (!commandPalette.hidden && handlePaletteKey(event)) return;
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    form.requestSubmit();
  }
});
input.addEventListener('input', () => {
  resizeInput();
  updateCommandPalette();
});
newChatButton.addEventListener('click', () => startNewChat(true));
agentCommandTrigger.addEventListener('click', () => openCommandSelector('agent', agentCommandTrigger));
skillCommandTrigger.addEventListener('click', () => openCommandSelector('skill', skillCommandTrigger));
commandPalette.addEventListener('keydown', (event) => {
  if (!handlePaletteKey(event)) return;
  if (!commandPalette.hidden) window.setTimeout(() => commandPalette.querySelector(`#palette-option-${state.paletteIndex}`)?.focus(), 0);
});
sidebarOpen.addEventListener('click', openMobileSidebar);
sidebarClose.addEventListener('click', () => closeMobileSidebar(true));
mobileOverlay.addEventListener('click', () => closeMobileSidebar(true));
$('settingsButton').addEventListener('click', openSettings);
$('settingsClose').addEventListener('click', closeSettings);
drawerBackdrop.addEventListener('click', closeSettings);
appRetry.addEventListener('click', initialize);
$('editKeyButton').addEventListener('click', () => {
  apiKeyEditor.hidden = false;
  apiKeyInput.focus();
});
$('testConnectionButton').addEventListener('click', testConnection);
$('restoreDefaultButton').addEventListener('click', restoreDefault);
$('runAgentDiagnosticsButton').addEventListener('click', runAgentDiagnostics);
$('agentDetailClose').addEventListener('click', closeAgentDetail);
$('agentDetailStart').addEventListener('click', startWithDetailedAgent);
$('skillDetailClose').addEventListener('click', closeSkillDetail);
$('skillDetailUse').addEventListener('click', startWithDetailedSkill);
$('approvalClose').addEventListener('click', () => closeApprovalDialog(false));
$('approvalCancel').addEventListener('click', () => submitApprovalDecision(false));
$('approvalConfirm').addEventListener('click', () => submitApprovalDecision(true));
agentDetailDialog.addEventListener('cancel', (event) => { event.preventDefault(); closeAgentDetail(); });
skillDetailDialog.addEventListener('cancel', (event) => { event.preventDefault(); closeSkillDetail(); });
approvalDialog.addEventListener('cancel', (event) => { event.preventDefault(); closeApprovalDialog(false); });
settingsForm.addEventListener('submit', saveSettings);
document.querySelectorAll('[data-prompt]').forEach((button) => {
  button.addEventListener('click', () => sendMessage(button.dataset.prompt));
});
document.querySelectorAll('[data-agent]').forEach((button) => {
  button.addEventListener('click', () => selectAgent(button.dataset.agent, {focus: true}));
});
document.querySelectorAll('[data-skill]').forEach((button) => {
  button.addEventListener('click', () => selectSkill(button.dataset.skill, {focus: true}));
});
document.addEventListener('click', (event) => {
  if (!event.target.closest('.history-item')) {
    state.openMenuId = null;
    renderHistory();
  }
  const clickPath = typeof event.composedPath === 'function' ? event.composedPath() : [];
  if (!clickPath.includes(agentCommandBar) && !event.target.closest('.composer')) closeCommandPalette();
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') {
    if (settingsDrawer.classList.contains('open')) closeSettings();
    else if (agentDetailDialog.open) closeAgentDetail();
    else if (approvalDialog.open) closeApprovalDialog(false);
    else if (sidebar.classList.contains('mobile-open')) closeMobileSidebar(true);
    else if (!commandPalette.hidden) closeCommandPalette();
  }
});
mobileSidebarQuery.addEventListener('change', syncSidebarA11y);
window.addEventListener('wenchang:languagechange', (event) => {
  state.language = event.detail?.language || i18n?.getLanguage() || 'zh-CN';
  languageInput.value = state.language;
  renderSelections();
  renderHistory();
  if (!commandPalette.hidden) renderCommandPalette();
  if (state.model) updateModelUi(state.model);
  void refreshKnowledgeStatus();
  void refreshToolServiceStatus();
});
languageInput.value = state.language;
syncSidebarA11y();

async function initialize() {
  if (state.initializing) return;
  state.initializing = true;
  state.hydrated = false;
  state.appState = 'APP_LOADING';
  root.dataset.appState = 'APP_LOADING';
  root.dataset.hydrated = 'false';
  main.setAttribute('aria-busy', 'true');
  appLoadingText.textContent = t('loading.restore');
  appRetry.hidden = true;
  clearHeroTransition();

  void refreshKnowledgeStatus();
  void refreshModelStatus();
  void refreshToolServiceStatus();

  try {
    await Promise.all([
      loadConversations(),
      loadAgentExperience().catch(() => showToast(t('palette.empty')))
    ]);
    const activeExists = state.activeConversationId
      && state.conversations.some((item) => item.id === state.activeConversationId);
    if (activeExists) {
      const detail = await apiJson(`/api/conversations/${state.activeConversationId}`);
      if (Array.isArray(detail.messages) && detail.messages.length) {
        renderConversationDetail(detail, state.activeConversationId);
        window.scrollTo({top: document.body.scrollHeight, behavior: 'auto'});
        finishHydration('CHAT');
      } else {
        prepareHome(true);
        finishHydration('HOME');
      }
    } else {
      prepareHome(true);
      finishHydration('HOME');
    }
  } catch (error) {
    appLoadingText.textContent = `会话加载失败：${error.message}`;
    appRetry.hidden = false;
  } finally {
    state.initializing = false;
    resizeInput();
  }
}

function finishHydration(mode) {
  setPageState(mode);
  state.hydrated = true;
  root.dataset.hydrated = 'true';
  main.setAttribute('aria-busy', 'false');
}

function setPageState(mode) {
  state.appState = mode;
  root.dataset.appState = mode;
  main.classList.toggle('chatting', mode === 'CHAT');
}

function renderConversationDetail(detail, id) {
  state.inlineEditor = null;
  state.activeConversationId = id;
  localStorage.setItem('wenchang-active-conversation', id);
  restoreConversationExperience(detail);
  messages.replaceChildren();
  state.renderingHistory = true;
  try {
    detail.messages.forEach(renderPersistedMessage);
  } finally {
    state.renderingHistory = false;
  }
  if (!messages.querySelector('.artifact-card')) void refreshConversationArtifacts(id);
  main.classList.add('chatting');
  renderHistory();
}

function prepareHome(clearActive = true) {
  clearHeroTransition();
  if (clearActive) {
    state.activeConversationId = null;
    localStorage.removeItem('wenchang-active-conversation');
  }
  messages.replaceChildren();
  main.classList.remove('chatting');
  state.openMenuId = null;
  renderHistory();
  input.value = '';
  closeCommandPalette();
  resizeInput();
}

function transitionHomeToChat() {
  if (!state.hydrated || state.appState !== 'HOME' || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    setPageState('CHAT');
    return;
  }
  clearHeroTransition();
  root.dataset.heroTransition = 'true';
  void hero.offsetHeight;
  setPageState('CHAT');
  const cleanup = () => clearHeroTransition();
  hero.addEventListener('transitionend', cleanup, {once: true});
  state.heroTimer = window.setTimeout(cleanup, 650);
}

function clearHeroTransition() {
  if (state.heroTimer) window.clearTimeout(state.heroTimer);
  state.heroTimer = null;
  delete root.dataset.heroTransition;
}

async function loadAgentExperience() {
  const [agents, skills] = await Promise.all([apiJson('/api/agents'), apiJson('/api/skills')]);
  state.agents = Array.isArray(agents) ? agents : [];
  state.skills = Array.isArray(skills) ? skills : [];
  if (!state.agents.some((agent) => agent.id === state.selectedAgentId)) state.selectedAgentId = 'wenchang';
  renderSelections();
}

function selectedAgent() {
  return state.agents.find((agent) => agent.id === state.selectedAgentId)
    || state.agents.find((agent) => agent.id === 'wenchang')
    || {id: 'wenchang', displayName: 'Wenchang Assistant', description: '综合文昌知识、实时信息与任务执行'};
}

function selectedSkill() {
  return state.skills.find((skill) => skill.id === state.selectedSkillId) || null;
}

function selectAgent(id, options = {}) {
  const agent = state.agents.find((item) => item.id === id);
  if (!agent) return;
  state.selectedAgentId = agent.id;
  state.agentSelectionExplicit = true;
  renderSelections();
  closeCommandPalette();
  if (options.focus) input.focus();
}

function selectSkill(id, options = {}) {
  const skill = state.skills.find((item) => item.id === id);
  if (!skill) return;
  state.selectedSkillId = skill.id;
  renderSelections();
  closeCommandPalette();
  if (options.focus) input.focus();
}

function renderSelections() {
  composerSelections.replaceChildren();
  const agent = selectedAgent();
  if (state.agentSelectionExplicit || agent.id !== 'wenchang') composerSelections.append(agentContextCard(agent));
  const skill = selectedSkill();
  if (skill) composerSelections.append(skillContextCard(skill));
  composerSelections.hidden = !composerSelections.childElementCount;
  commandIdle.hidden = Boolean(composerSelections.childElementCount) || !commandPalette.hidden;
  syncCommandState();
}

function agentContextCard(agent) {
  const card = document.createElement('section');
  card.className = 'agent-context-card';
  const copy = document.createElement('div');
  const name = document.createElement('strong');
  name.textContent = agentName(agent);
  const summary = document.createElement('span');
  summary.textContent = agent.contextSummary || agentDescription(agent);
  const skillLine = document.createElement('small');
  const available = agentSkills(agent).map((id) => skillById(id)?.command).filter(Boolean).slice(0, 5);
  skillLine.textContent = available.length
    ? t('context.availableSkills', {value: available.join('  ')})
    : t('context.agentReady');
  copy.append(name, summary, skillLine);
  const actions = document.createElement('div');
  actions.className = 'context-actions';
  const detail = document.createElement('button');
  detail.type = 'button';
  detail.className = 'agent-context-detail';
  detail.textContent = t('context.viewCapabilities');
  detail.addEventListener('click', () => openAgentDetail(agent.id, detail));
  const switchButton = document.createElement('button');
  switchButton.type = 'button';
  switchButton.className = 'agent-context-switch';
  switchButton.textContent = t('context.switch');
  switchButton.addEventListener('click', () => openCommandSelector('agent', switchButton));
  const skillButton = document.createElement('button');
  skillButton.type = 'button';
  skillButton.className = 'agent-context-skill';
  skillButton.textContent = t('context.useSkill');
  skillButton.addEventListener('click', () => openCommandSelector('skill', skillButton));
  const remove = document.createElement('button');
  remove.type = 'button';
  remove.className = 'agent-context-remove';
  remove.textContent = '×';
  remove.setAttribute('aria-label', `移除 ${agent.displayName}`);
  remove.addEventListener('click', () => {
    state.selectedAgentId = 'wenchang';
    state.agentSelectionExplicit = false;
    renderSelections();
    input.focus();
  });
  actions.append(detail, switchButton, skillButton, remove);
  card.append(copy, actions);
  return card;
}

function skillContextCard(skill) {
  const card = document.createElement('section');
  card.className = 'skill-context-card';
  const copy = document.createElement('div');
  const name = document.createElement('strong');
  name.textContent = skill.command;
  const description = document.createElement('span');
  description.textContent = skillDescription(skill);
  const inputHint = document.createElement('small');
  inputHint.textContent = t('context.requiredInput', {value: skillInputHint(skill)});
  copy.append(name, description, inputHint);
  const actions = document.createElement('div');
  actions.className = 'context-actions';
  const explain = document.createElement('button');
  explain.type = 'button';
  explain.className = 'skill-context-detail';
  explain.textContent = t('context.viewDetails');
  explain.addEventListener('click', () => openSkillDetail(skill.id, explain));
  const switchButton = document.createElement('button');
  switchButton.type = 'button';
  switchButton.className = 'skill-context-switch';
  switchButton.textContent = t('context.switch');
  switchButton.addEventListener('click', () => openCommandSelector('skill', switchButton));
  const remove = document.createElement('button');
  remove.type = 'button';
  remove.textContent = '×';
  remove.className = 'skill-context-remove';
  remove.setAttribute('aria-label', `移除 ${skill.command}`);
  remove.addEventListener('click', () => {
    state.selectedSkillId = null;
    renderSelections();
    input.focus();
  });
  actions.append(explain, switchButton, remove);
  card.append(copy, actions);
  return card;
}

function updateCommandPalette() {
  const trigger = commandTrigger();
  if (!trigger) {
    closeCommandPalette();
    return;
  }
  openCommandSelector(trigger.type, input, trigger.query, true);
}

function openCommandSelector(type, triggerElement = null, query = '', fromInput = false) {
  if (!['agent', 'skill'].includes(type)) return;
  const all = type === 'agent' ? state.agents : orderedSkills();
  const normalizedQuery = String(query || '').toLowerCase();
  state.paletteType = type;
  state.paletteOpenedFromInput = fromInput;
  state.commandTriggerElement = triggerElement;
  state.paletteItems = all.filter((item) => {
    const value = type === 'agent'
      ? [agentName(item), agentDescription(item), item.contextSummary, item.id].filter(Boolean).join(' ').toLowerCase()
      : [item.command, skillDescription(item), item.id, skillOutput(item)].filter(Boolean).join(' ').toLowerCase();
    return !normalizedQuery || value.includes(normalizedQuery);
  });
  state.paletteIndex = 0;
  renderCommandPalette();
  renderSelections();
  if (!fromInput) window.setTimeout(() => commandPalette.querySelector('.palette-option')?.focus(), 0);
}

function commandTrigger() {
  const caret = input.selectionStart ?? input.value.length;
  const before = input.value.slice(0, caret);
  const match = before.match(/(?:^|\s)([@/])([^\s@/]*)$/u);
  if (!match) return null;
  const symbolOffset = match[0].lastIndexOf(match[1]);
  return {
    type: match[1] === '@' ? 'agent' : 'skill',
    query: match[2],
    start: match.index + symbolOffset,
    end: caret
  };
}

function renderCommandPalette() {
  commandPalette.replaceChildren();
  commandPalette.hidden = false;
  const header = document.createElement('div');
  header.className = 'palette-header';
  header.innerHTML = `<strong>${t(state.paletteType === 'agent' ? 'palette.agentTitle' : 'palette.skillTitle')}</strong><span>${t('palette.keyboardHint')}</span>`;
  commandPalette.append(header);
  if (!state.paletteItems.length) {
    const empty = document.createElement('div');
    empty.className = 'palette-empty';
    empty.textContent = t('palette.empty');
    commandPalette.append(empty);
    return;
  }
  let activeGroup = null;
  let grid = null;
  state.paletteItems.forEach((item, index) => {
    if (state.paletteType === 'skill' && skillGroupKey(item) !== activeGroup) {
      activeGroup = skillGroupKey(item);
      const group = document.createElement('section');
      group.className = 'palette-group';
      const title = document.createElement('h3');
      title.textContent = skillGroupLabel(item);
      grid = document.createElement('div');
      grid.className = 'palette-grid skill-grid';
      group.append(title, grid);
      commandPalette.append(group);
    } else if (state.paletteType === 'agent' && !grid) {
      grid = document.createElement('div');
      grid.className = 'palette-grid agent-grid';
      commandPalette.append(grid);
    }
    const button = document.createElement('button');
    button.type = 'button';
    button.id = `palette-option-${index}`;
    button.className = `palette-option${index === state.paletteIndex ? ' active' : ''}`;
    button.setAttribute('role', 'option');
    button.setAttribute('aria-selected', String(index === state.paletteIndex));
    const copy = document.createElement('span');
    const name = document.createElement('strong');
    name.textContent = state.paletteType === 'agent' ? agentName(item) : item.command;
    const description = document.createElement('small');
    description.textContent = state.paletteType === 'agent' ? agentDescription(item) : skillDescription(item);
    const meta = document.createElement('em');
    meta.textContent = state.paletteType === 'agent'
      ? (item.contextSummary || agentCapabilities(item).slice(0, 3).join(' · '))
      : t('palette.output', {value: skillOutput(item)});
    copy.append(name, description, meta);
    button.append(copy);
    button.addEventListener('mousedown', (event) => event.preventDefault());
    button.addEventListener('click', () => choosePaletteItem(index));
    if (state.paletteType === 'skill') {
      const shell = document.createElement('div');
      shell.className = 'palette-option-shell';
      const explain = document.createElement('button');
      explain.type = 'button';
      explain.className = 'palette-option-detail';
      explain.textContent = t('palette.viewDetails');
      explain.setAttribute('aria-label', `${t('palette.viewDetails')} ${item.command}`);
      explain.addEventListener('mousedown', (event) => event.preventDefault());
      explain.addEventListener('click', (event) => {
        event.stopPropagation();
        openSkillDetail(item.id, explain);
      });
      shell.append(button, explain);
      grid.append(shell);
    } else {
      grid.append(button);
    }
  });
  input.setAttribute('aria-controls', 'commandPalette');
  input.setAttribute('aria-expanded', 'true');
  input.setAttribute('aria-activedescendant', `palette-option-${state.paletteIndex}`);
}

function handlePaletteKey(event) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    const direction = event.key === 'ArrowDown' ? 1 : -1;
    const length = state.paletteItems.length;
    if (length) state.paletteIndex = (state.paletteIndex + direction + length) % length;
    renderCommandPalette();
    return true;
  }
  if ((event.key === 'Enter' || event.key === 'Tab') && !event.shiftKey && state.paletteItems.length) {
    event.preventDefault();
    choosePaletteItem(state.paletteIndex);
    return true;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    const returnTarget = state.commandTriggerElement;
    closeCommandPalette();
    if (returnTarget instanceof HTMLElement) returnTarget.focus();
    return true;
  }
  return false;
}

function choosePaletteItem(index) {
  const item = state.paletteItems[index];
  const trigger = commandTrigger();
  if (!item) return;
  if (trigger && state.paletteOpenedFromInput) {
    input.setRangeText('', trigger.start, trigger.end, 'end');
    input.value = input.value.replace(/^\s+/, '');
  }
  if (state.paletteType === 'agent') selectAgent(item.id);
  else selectSkill(item.id);
  closeCommandPalette();
  resizeInput();
  input.focus();
}

function closeCommandPalette() {
  commandPalette.hidden = true;
  commandPalette.replaceChildren();
  state.paletteType = null;
  state.paletteItems = [];
  state.paletteIndex = 0;
  state.paletteOpenedFromInput = false;
  agentCommandTrigger.setAttribute('aria-expanded', 'false');
  skillCommandTrigger.setAttribute('aria-expanded', 'false');
  input.removeAttribute('aria-controls');
  input.removeAttribute('aria-expanded');
  input.removeAttribute('aria-activedescendant');
  renderSelections();
}

function syncCommandState() {
  let next = 'COMMAND_IDLE';
  if (!commandPalette.hidden) next = state.paletteType === 'agent' ? 'AGENT_SELECTING' : 'SKILL_SELECTING';
  else if (state.agentSelectionExplicit && state.selectedSkillId) next = 'AGENT_AND_SKILL_SELECTED';
  else if (state.agentSelectionExplicit) next = 'AGENT_SELECTED';
  else if (state.selectedSkillId) next = 'SKILL_SELECTED';
  if (agentDetailDialog.open || skillDetailDialog.open) next = 'DETAIL_OPEN';
  state.commandState = next;
  agentCommandBar.dataset.commandState = next;
  agentCommandTrigger.setAttribute('aria-expanded', String(next === 'AGENT_SELECTING'));
  skillCommandTrigger.setAttribute('aria-expanded', String(next === 'SKILL_SELECTING'));
}

async function loadConversations() {
  state.conversations = await apiJson('/api/conversations');
  renderHistory();
}

function renderHistory() {
  history.replaceChildren();
  if (!state.conversations.length) {
    history.append(historyEmpty);
    historyEmpty.hidden = false;
    return;
  }
  const groups = new Map([['today', []], ['yesterday', []], ['recent', []], ['older', []]]);
  state.conversations.forEach((conversation) => groups.get(dateGroup(conversation.updatedAt)).push(conversation));
  groups.forEach((items, label) => {
    if (!items.length) return;
    const section = document.createElement('section');
    section.className = 'history-group';
    const heading = document.createElement('h3');
    heading.textContent = t(`history.${label}`);
    section.append(heading);
    items.forEach((conversation) => section.append(historyItem(conversation)));
    history.append(section);
  });
}

function historyItem(conversation) {
  const wrapper = document.createElement('div');
  wrapper.className = `history-item${conversation.id === state.activeConversationId ? ' active' : ''}`;
  wrapper.dataset.id = conversation.id;

  const select = document.createElement('button');
  select.type = 'button';
  select.className = 'history-select';
  select.textContent = conversation.title;
  select.title = conversation.title;
  select.addEventListener('click', () => openConversation(conversation.id));

  const more = document.createElement('button');
  more.type = 'button';
  more.className = 'history-more';
  more.textContent = '•••';
  more.setAttribute('aria-label', `管理对话：${conversation.title}`);
  more.addEventListener('click', (event) => {
    event.stopPropagation();
    state.openMenuId = state.openMenuId === conversation.id ? null : conversation.id;
    renderHistory();
  });
  wrapper.append(select, more);

  if (state.openMenuId === conversation.id) {
    const menu = document.createElement('div');
    menu.className = 'history-menu';
    const rename = document.createElement('button');
    rename.type = 'button';
    rename.textContent = t('history.rename');
    rename.addEventListener('click', (event) => { event.stopPropagation(); beginRename(conversation); });
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'danger';
    remove.textContent = t('history.delete');
    remove.addEventListener('click', (event) => { event.stopPropagation(); deleteConversation(conversation); });
    menu.append(rename, remove);
    wrapper.append(menu);
  }
  return wrapper;
}

function beginRename(conversation) {
  state.openMenuId = null;
  renderHistory();
  const wrapper = history.querySelector(`[data-id="${CSS.escape(conversation.id)}"]`);
  if (!wrapper) return;
  const select = wrapper.querySelector('.history-select');
  const editor = document.createElement('input');
  editor.className = 'rename-input';
  editor.value = conversation.title;
  editor.maxLength = 80;
  select.replaceWith(editor);
  editor.focus();
  editor.select();
  let finished = false;
  const finish = async (save) => {
    if (finished) return;
    finished = true;
    const title = editor.value.trim();
    if (save && title && title !== conversation.title) {
      try {
        await apiJson(`/api/conversations/${conversation.id}`, {
          method: 'PATCH', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({title})
        });
      } catch (error) { showToast(error.message); }
    }
    await loadConversations();
  };
  editor.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') { event.preventDefault(); finish(true); }
    if (event.key === 'Escape') { event.preventDefault(); finish(false); }
  });
  editor.addEventListener('blur', () => finish(true));
}

async function deleteConversation(conversation) {
  state.openMenuId = null;
  if (!window.confirm(`确定删除“${conversation.title}”吗？该对话及消息将无法恢复。`)) {
    renderHistory();
    return;
  }
  try {
    const response = await fetch(appUrl(`/api/conversations/${conversation.id}`), {method: 'DELETE'});
    if (!response.ok) throw new Error(await readError(response));
    if (state.activeConversationId === conversation.id) startNewChat(false);
    await loadConversations();
    showToast('对话已删除');
  } catch (error) { showToast(error.message); }
}

async function openConversation(id, closeMobile = true) {
  if (state.busy) return;
  try {
    const detail = await apiJson(`/api/conversations/${id}`);
    clearHeroTransition();
    renderConversationDetail(detail, id);
    setPageState('CHAT');
    if (closeMobile) closeMobileSidebar(false);
    window.scrollTo({top: document.body.scrollHeight, behavior: 'auto'});
  } catch (error) {
    showToast(error.message);
    await loadConversations();
  }
}

function renderPersistedMessage(message) {
  const sources = parseJson(message.sourcesJson, []);
  const toolsUsed = parseJson(message.toolsUsedJson || message.toolsJson, []);
  const agentRun = parseJson(message.agentRunJson, null);
  const artifacts = parseJson(message.artifactsJson || message.artifactJson || message.artifacts, []);
  appendMessage(message.role === 'USER' ? 'user' : 'assistant', message.content, {
    sources, toolsUsed, modelProvider: message.modelProvider, modelName: message.modelName,
    traceId: message.traceId, agentId: message.agentId, skillId: message.skillId, agentRun, artifacts,
    messageId: message.id, revisions: message.revisions || [], revisionIndex: message.revisionIndex
  });
}

function restoreConversationExperience(detail) {
  const experienceMessage = Array.isArray(detail.messages)
    ? [...detail.messages].reverse().find((message) => message.agentId || message.skillId)
    : null;
  state.selectedAgentId = detail.agentId || experienceMessage?.agentId || 'wenchang';
  state.selectedSkillId = detail.skillId || experienceMessage?.skillId || null;
  state.agentSelectionExplicit = Boolean(detail.agentId || experienceMessage?.agentId);
  renderSelections();
}

function startNewChat(focus = true, agentId = 'wenchang') {
  if (state.busy) return;
  state.selectedAgentId = state.agents.some((agent) => agent.id === agentId) ? agentId : 'wenchang';
  state.selectedSkillId = null;
  state.agentSelectionExplicit = agentId !== 'wenchang';
  prepareHome(true);
  setPageState('HOME');
  renderSelections();
  closeMobileSidebar(false);
  if (focus) input.focus();
}

async function sendMessage(raw, options = {}) {
  const text = String(raw || '').trim();
  if (!text || state.busy) return;
  const editMessageId = Number(options.editMessageId) || null;
  const animateHero = !editMessageId && state.hydrated && state.appState === 'HOME'
    && !state.activeConversationId && messages.childElementCount === 0;
  if (animateHero) transitionHomeToChat();
  else setPageState('CHAT');
  const agent = options.agentId ? agentById(options.agentId) : selectedAgent();
  const skill = options.skillId ? skillById(options.skillId) : selectedSkill();

  if (editMessageId) {
    const existing = messages.querySelector('[data-message-id="' + CSS.escape(String(editMessageId)) + '"]');
    if (existing) {
      let sibling = existing.nextSibling;
      while (sibling) {
        const next = sibling.nextSibling;
        sibling.remove();
        sibling = next;
      }
      existing.querySelector('.message-content').textContent = text;
      existing.querySelector('.message-actions')?.remove();
      existing.removeAttribute('data-message-id');
    } else {
      appendMessage('user', text, {agentId: agent.id, skillId: skill?.id});
    }
  } else {
    appendMessage('user', text, {agentId: agent.id, skillId: skill?.id});
  }

  input.value = '';
  closeCommandPalette();
  resizeInput();
  setChatBusy(true);
  setProgress(skill ? '正在启动' + skillName(skill) + '…' : '正在连接' + agentName(agent) + '…');
  const assistant = appendMessage('assistant', '', {agentId: agent.id, skillId: skill?.id});
  const run = agent.id !== 'wenchang' || skill ? createAgentRun(assistant.element, agent, skill) : null;
  state.activeRun = run;

  try {
    const response = await fetch(appUrl('/api/chat/stream'), {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        message: text,
        conversationId: state.activeConversationId,
        agentId: agent.id,
        skillId: skill?.id || null,
        editMessageId
      })
    });
    if (!response.ok) throw new Error(await readError(response));
    await consumeSse(response, async (event, data) => {
      if (event === 'conversation') {
        state.activeConversationId = data.id;
        localStorage.setItem('wenchang-active-conversation', data.id);
        await loadConversations();
      } else if (event === 'status') {
        setProgress(data.message || '正在处理…');
        if (run && !run.namedEvents) updateCompatibilityRun(run, data);
      } else if (event === 'chunk' || event === 'answer_chunk') {
        appendMarkdownChunk(assistant, data.text || '');
        scrollToLatest();
      } else if (['agent_selected', 'skill_selected', 'plan_created', 'step_started', 'tool_started',
        'tool_completed', 'source_found', 'step_completed'].includes(event)) {
        if (run) applyAgentRunEvent(run, event, data);
      } else if (event === 'approval_required') {
        openApprovalDialog(data);
      } else if (event === 'artifact_created') {
        addArtifactCards(assistant.element, [data]);
      } else if (event === 'complete') {
        finalizeAssistantMarkdown(assistant, data.answer || '');
        if (run) completeAgentRun(run, data);
        addMessageMeta(assistant.element, {...data, agentId: data.agentId || agent.id, skillId: data.skillId || skill?.id});
        const completedArtifacts = data.artifacts || data.agentRun?.artifacts || [];
        addArtifactCards(assistant.element, completedArtifacts);
        if (!Array.isArray(completedArtifacts) || !completedArtifacts.length) {
          await refreshConversationArtifacts(state.activeConversationId, assistant.element);
        }
        await reloadActiveConversation();
        await loadConversations();
      } else if (event === 'error') {
        throw new Error(data.message || '生成回答失败');
      }
    });
  } catch (error) {
    finalizeAssistantMarkdown(assistant, '抱歉，本次回答没有完成：' + error.message);
    assistant.element.classList.add('error');
    if (run) failAgentRun(run);
    if (editMessageId && state.activeConversationId) await reloadActiveConversation();
  } finally {
    state.activeRun = null;
    setChatBusy(false);
    progress.hidden = true;
    input.focus();
    scrollToLatest();
  }
}

async function reloadActiveConversation(focusMessageId = null) {
  if (!state.activeConversationId) return;
  const detail = await apiJson('/api/conversations/' + encodeURIComponent(state.activeConversationId));
  renderConversationDetail(detail, state.activeConversationId);
  setPageState('CHAT');
  if (focusMessageId) {
    const target = messages.querySelector('[data-message-id="' + CSS.escape(String(focusMessageId)) + '"]');
    target?.scrollIntoView({block: 'center', behavior: 'smooth'});
  } else {
    scrollToLatest();
  }
}
function appendMessage(role, content, metadata = null) {
  const element = document.createElement('article');
  element.className = `message ${role}`;
  if (metadata?.messageId) element.dataset.messageId = String(metadata.messageId);
  if (metadata?.agentId) element.dataset.agentId = metadata.agentId;
  if (metadata?.skillId) element.dataset.skillId = metadata.skillId;
  if (role === 'assistant') {
    const head = document.createElement('div');
    head.className = 'message-head';
    const profile = agentById(metadata?.agentId);
    const avatar = profile.id === 'wenchang' ? document.createElement('span') : null;
    if (profile.id === 'wenchang') {
      avatar.className = 'avatar';
      const logo = document.createElement('img');
      logo.src = appUrl('/assets/wenchang-logo.svg');
      logo.alt = '';
      avatar.append(logo);
    }
    const name = document.createElement('b');
    name.textContent = agentName(profile);
    if (avatar) head.append(avatar);
    head.append(name);
    element.append(head);
  }
  if (metadata && (metadata.agentId && metadata.agentId !== 'wenchang' || metadata.skillId)) {
    element.append(messageContext(metadata.agentId, metadata.skillId));
  }
  const body = document.createElement('div');
  body.className = 'message-content';
  if (role === 'assistant') renderMarkdown(content || '', body);
  else body.textContent = content || '';
  element.append(body);
  if (role === 'user') element.append(createMessageActions(content || '', metadata || {}, element));
  messages.append(element);
  if (role === 'assistant' && metadata?.agentRun) renderPersistedAgentRun(element, metadata.agentRun, metadata);
  if (metadata) addMessageMeta(element, metadata);
  if (role === 'assistant') addArtifactCards(element, metadata?.artifacts || []);
  if (!state.renderingHistory) scrollToLatest();
  return {element, content: body, rawMarkdownBuffer: content || '', markdownTimer: null};
}

function createMessageActions(content, metadata = {}, messageElement) {
  const actions = document.createElement('div');
  actions.className = 'message-actions';
  actions.setAttribute('aria-label', t('message.actions'));
  actions.dataset.i18nAriaLabel = 'message.actions';

  const revisions = Array.isArray(metadata.revisions) ? metadata.revisions : [];
  if (revisions.length > 1) actions.append(createRevisionNavigator(revisions));

  const copy = messageActionButton('copy', 'message.copy', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="8" y="8" width="11" height="11" rx="2"></rect><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"></path></svg>');
  copy.addEventListener('click', () => copyMessageText(content, copy));
  actions.append(copy);

  if (metadata.messageId) {
    const edit = messageActionButton('edit', 'message.edit', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20h4l10.5-10.5a2.1 2.1 0 0 0-4-4L4 16v4Z"></path><path d="m13.5 6.5 4 4"></path></svg>');
    edit.addEventListener('click', () => editUserMessage(content, metadata, messageElement));
    actions.append(edit);
  }
  return actions;
}

function createRevisionNavigator(revisions) {
  const ordered = [...revisions].sort((a, b) => Number(a.index) - Number(b.index));
  const currentIndex = Math.max(0, ordered.findIndex((item) => item.active));
  const navigation = document.createElement('span');
  navigation.className = 'message-revisions';
  navigation.setAttribute('aria-label', t('message.versions'));

  const previous = revisionButton(t('message.previousVersion'), '‹');
  const next = revisionButton(t('message.nextVersion'), '›');
  previous.disabled = currentIndex <= 0;
  next.disabled = currentIndex >= ordered.length - 1;
  if (!previous.disabled) previous.addEventListener('click', () => activateMessageRevision(ordered[currentIndex - 1].messageId));
  if (!next.disabled) next.addEventListener('click', () => activateMessageRevision(ordered[currentIndex + 1].messageId));

  const position = document.createElement('span');
  position.className = 'message-revision-position';
  position.textContent = (currentIndex + 1) + ' / ' + ordered.length;
  navigation.append(previous, position, next);
  return navigation;
}

function revisionButton(label, symbol) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'message-revision-button';
  button.setAttribute('aria-label', label);
  button.title = label;
  button.textContent = symbol;
  return button;
}

async function activateMessageRevision(messageId) {
  if (state.busy || !state.activeConversationId) return;
  try {
    const detail = await apiJson('/api/conversations/' + encodeURIComponent(state.activeConversationId)
      + '/messages/' + encodeURIComponent(messageId) + '/activate', {method: 'POST'});
    state.inlineEditor = null;
    renderConversationDetail(detail, state.activeConversationId);
    setPageState('CHAT');
    const target = messages.querySelector('[data-message-id="' + CSS.escape(String(messageId)) + '"]');
    target?.scrollIntoView({block: 'center', behavior: 'smooth'});
    await loadConversations();
  } catch (error) {
    showToast(error.message);
  }
}

function messageActionButton(kind, labelKey, icon) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'message-action ' + kind;
  button.dataset.i18nAriaLabel = labelKey;
  button.setAttribute('aria-label', t(labelKey));
  button.innerHTML = icon;
  const label = document.createElement('span');
  label.dataset.i18n = labelKey;
  label.textContent = t(labelKey);
  button.append(label);
  return button;
}

async function copyMessageText(content, button) {
  try {
    const value = String(content || '');
    let copied = false;
    if (navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(value);
        copied = true;
      } catch { /* HTTP or permission-restricted browser: use the user-gesture fallback below. */ }
    }
    if (!copied) fallbackCopyText(value);
    button.classList.add('success');
    window.setTimeout(() => button.classList.remove('success'), 1200);
    showToast(t('message.copied'));
  } catch {
    showToast(t('message.copyFailed'));
  }
}

function fallbackCopyText(value) {
  const area = document.createElement('textarea');
  area.value = value;
  area.setAttribute('readonly', '');
  area.style.position = 'fixed';
  area.style.opacity = '0';
  document.body.append(area);
  area.select();
  const copied = document.execCommand('copy');
  area.remove();
  if (!copied) throw new Error('COPY_FAILED');
}

function editUserMessage(content, metadata, messageElement) {
  if (state.busy) {
    showToast(t('message.waitForReply'));
    return;
  }
  if (!metadata?.messageId || !messageElement) {
    showToast(t('message.editUnavailable'));
    return;
  }
  state.inlineEditor?.cancel?.();

  const body = messageElement.querySelector('.message-content');
  const actions = messageElement.querySelector('.message-actions');
  const editor = document.createElement('textarea');
  editor.className = 'message-inline-editor';
  editor.value = String(content || '');
  editor.setAttribute('aria-label', t('message.editQuestion'));

  const controls = document.createElement('div');
  controls.className = 'message-inline-controls';
  const cancel = document.createElement('button');
  cancel.type = 'button';
  cancel.className = 'message-inline-cancel';
  cancel.textContent = t('common.cancel');
  const submit = document.createElement('button');
  submit.type = 'button';
  submit.className = 'message-inline-submit';
  submit.textContent = t('message.sendEdited');

  const restore = () => {
    body.classList.remove('inline-editing');
    body.textContent = String(content || '');
    if (actions) actions.hidden = false;
    if (state.inlineEditor?.messageId === metadata.messageId) state.inlineEditor = null;
  };
  const submitEdit = async () => {
    const value = editor.value.trim();
    if (!value) {
      showToast(t('message.emptyEdit'));
      editor.focus();
      return;
    }
    if (value === String(content || '').trim()) {
      restore();
      return;
    }
    state.inlineEditor = null;
    await sendMessage(value, {
      editMessageId: metadata.messageId,
      agentId: metadata.agentId || null,
      skillId: metadata.skillId || null
    });
  };

  cancel.addEventListener('click', restore);
  submit.addEventListener('click', submitEdit);
  editor.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      restore();
    } else if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      submitEdit();
    }
  });

  controls.append(cancel, submit);
  body.classList.add('inline-editing');
  body.replaceChildren(editor, controls);
  if (actions) actions.hidden = true;
  state.inlineEditor = {messageId: metadata.messageId, cancel: restore};
  editor.focus();
  editor.setSelectionRange(editor.value.length, editor.value.length);
}
function renderMarkdown(markdown, target) {
  const raw = String(markdown || '');
  if (!window.marked?.parse || !window.DOMPurify?.sanitize) {
    target.textContent = raw;
    return;
  }
  const parsed = window.marked.parse(raw, {gfm: true, breaks: false});
  target.innerHTML = window.DOMPurify.sanitize(parsed, {USE_PROFILES: {html: true}});
  target.querySelectorAll('a[href]').forEach((link) => {
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
  });
}

function appendMarkdownChunk(message, chunk) {
  message.rawMarkdownBuffer += String(chunk || '');
  window.clearTimeout(message.markdownTimer);
  message.markdownTimer = window.setTimeout(() => {
    renderMarkdown(message.rawMarkdownBuffer, message.content);
    scrollToLatest();
  }, 45);
}

function finalizeAssistantMarkdown(message, finalAnswer = '') {
  window.clearTimeout(message.markdownTimer);
  if (String(finalAnswer || '').length) message.rawMarkdownBuffer = String(finalAnswer);
  renderMarkdown(message.rawMarkdownBuffer, message.content);
}

function agentById(id) {
  return state.agents.find((agent) => agent.id === id)
    || state.agents.find((agent) => agent.id === 'wenchang')
    || {id: 'wenchang', displayName: 'Wenchang Assistant'};
}

function skillById(id) {
  return state.skills.find((skill) => skill.id === id) || null;
}

function agentName(agent = {}) {
  return agent.displayNameEn || agent.displayName || 'Wenchang Assistant';
}

function agentDescription(agent = {}) {
  return agent.descriptionZh || agent.description || '综合文昌知识、实时信息与任务执行';
}

function agentCapabilities(agent = {}) {
  return agent.capabilitiesZh || agent.taskCapabilities || [];
}

function agentInputs(agent = {}) {
  return agent.acceptedInputsZh || agent.acceptedInputs || [];
}

function agentSkills(agent = {}) {
  return agent.skills || agent.suggestedSkills || [];
}

function agentTools(agent = {}) {
  return agent.tools || agent.preferredTools || [];
}

function agentWorkflow(agent = {}) {
  return agent.workflowZh || agent.typicalWorkflow || [];
}

function agentApproval(agent = {}) {
  return agent.humanApprovalZh || agent.humanInTheLoop || '执行长期资产变更前会请求确认。';
}

function agentExamples(agent = {}) {
  return agent.examplesZh || agent.exampleTasks || [];
}

function skillName(skill = {}) {
  return skill.displayNameZh || skill.displayName || skill.command || '技能';
}

function skillDescription(skill = {}) {
  return skill.descriptionZh || skill.description || '';
}

function skillInputHint(skill = {}) {
  if (skill.inputHintZh) return skill.inputHintZh;
  const hints = {
    'deep-research': '研究主题 · 时间范围 · 期望成果',
    'official-search': '明确主题 · 机构范围 · 时间范围',
    'evidence-check': '待核验结论 · 已有来源',
    'word-report': '报告标题 · 主题 · 内容重点',
    'policy-brief': '政策主题 · 时间范围 · 关注领域',
    'data-export': '数据类型 · 字段 · CSV 或 Excel',
    'study-tour-plan': '年龄 · 时间 · 主题 · 人数 · 偏好',
    'place-search': '地点关键词 · 乡镇 · 主题 · 年龄',
    'public-service': '服务类型 · 所在乡镇 · 具体需求'
  };
  return hints[skill.id] || '任务主题 · 范围 · 期望输出';
}

function skillOutput(skill = {}) {
  return skill.outputZh || skill.outputType || '任务结果';
}

function skillGroupKey(skill = {}) {
  if (['word-report', 'policy-brief', 'data-export'].includes(skill.id)) return 'TASK_FILES';
  if (['study-tour-plan', 'place-search', 'public-service'].includes(skill.id)) return 'PLANNING_SERVICE';
  return 'RESEARCH_SEARCH';
}

function skillGroupLabel(skill = {}) {
  return {
    RESEARCH_SEARCH: t('group.research'),
    TASK_FILES: t('group.files'),
    PLANNING_SERVICE: t('group.planning')
  }[skillGroupKey(skill)] || t('group.other');
}

function orderedSkills() {
  const order = new Map([
    ['deep-research', 0], ['official-search', 1], ['evidence-check', 2], ['web-search', 3],
    ['policy-search', 4], ['latest-policy', 5], ['policy-compare', 6], ['word-report', 10],
    ['policy-brief', 11], ['data-export', 12], ['study-tour-plan', 20], ['place-search', 21],
    ['public-service', 22]
  ]);
  return [...state.skills].sort((left, right) => (order.get(left.id) ?? 99) - (order.get(right.id) ?? 99));
}

function messageContext(agentId, skillId) {
  const context = document.createElement('div');
  context.className = 'message-context';
  const agent = agentById(agentId);
  if (agent.id !== 'wenchang') context.append(contextChip(agentName(agent)));
  const skill = skillById(skillId);
  if (skill) context.append(contextChip(skill.command));
  return context;
}

function contextChip(label) {
  const chip = document.createElement('span');
  chip.className = 'context-chip';
  chip.textContent = label;
  return chip;
}

function createAgentRun(element, agent, skill) {
  const details = document.createElement('details');
  details.className = 'agent-run';
  details.open = true;
  const summary = document.createElement('summary');
  const indicator = document.createElement('span');
  indicator.className = 'run-indicator';
  indicator.setAttribute('aria-hidden', 'true');
  const summaryText = document.createElement('span');
  summaryText.textContent = `${agentName(agent)} 正在执行${skill ? ` · ${skill.command}` : ''}`;
  summary.append(indicator, summaryText);
  const list = document.createElement('div');
  list.className = 'agent-run-steps';
  details.append(summary, list);
  element.querySelector('.message-content').before(details);
  const outcomes = document.createElement('section');
  outcomes.className = 'agent-run-artifacts';
  outcomes.hidden = true;
  details.append(outcomes);
  const run = {details, summaryText, list, outcomes, steps: new Map(), tools: new Set(), sourceCount: 0, namedEvents: false};
  upsertRunStep(run, {id: 'prepare', title: '正在制定公开任务计划', status: 'running'});
  return run;
}

function applyAgentRunEvent(run, event, data = {}) {
  run.namedEvents = true;
  if (event === 'plan_created') {
    run.steps.clear();
    run.list.replaceChildren();
    const steps = Array.isArray(data.steps) ? data.steps
      : Array.isArray(data.plan?.steps) ? data.plan.steps
        : Array.isArray(data.agentRun?.steps) ? data.agentRun.steps : [];
    steps.forEach((step, index) => upsertRunStep(run, {
      id: step.id || `step-${index + 1}`,
      title: step.title || step.label || step.message || `任务步骤 ${index + 1}`,
      status: step.status || (index === 0 ? 'running' : 'pending'),
      tool: step.tool || (Array.isArray(step.tools) ? step.tools.join('、') : '')
    }));
    if (!steps.length) upsertRunStep(run, {id: 'plan', title: data.message || '任务计划已建立', status: 'complete'});
  } else if (event === 'step_started' || event === 'step_completed') {
    upsertRunStep(run, {
      id: data.stepId || data.id || `step-${run.steps.size + 1}`,
      title: data.title || data.label || data.message || data.step || '执行任务步骤',
      status: event === 'step_completed' ? data.status || 'complete' : 'running',
      tool: data.toolName || data.tool || '',
      toolSource: data.toolSource,
      latencyMs: data.latencyMs,
      error: data.error || data.errorMessage,
      errorType: data.errorType
    });
  } else if (event === 'tool_started' || event === 'tool_completed') {
    const tool = data.toolName || data.name || data.tool || 'tool';
    run.tools.add(tool);
    upsertRunStep(run, {
      id: `tool-${tool}`,
      title: `${event === 'tool_started' ? '调用' : '已完成'}${toolLabel(tool)}`,
      status: event === 'tool_completed' ? data.status || 'complete' : 'running',
      tool,
      toolSource: data.toolSource,
      latencyMs: data.latencyMs,
      error: data.error || data.errorMessage,
      errorType: data.errorType
    });
  } else if (event === 'source_found') {
    run.sourceCount = Number(data.count) || run.sourceCount + 1;
  }
  run.summaryText.textContent = runningRunSummary(run);
}

function updateCompatibilityRun(run, data = {}) {
  const stage = data.stage || 'working';
  const stageMap = {
    retrieval: '检索文昌知识库', retrieved: '知识资料检索完成', generation: '整理并生成回答',
    tool: '调用任务工具', completed: '任务执行完成'
  };
  if (stage === 'retrieved' && Number(data.count)) run.sourceCount = Number(data.count);
  upsertRunStep(run, {
    id: `status-${stage}`,
    title: data.message || stageMap[stage] || '执行任务',
    status: stage === 'retrieved' || stage === 'completed' ? 'complete' : 'running'
  });
  run.summaryText.textContent = runningRunSummary(run);
}

function upsertRunStep(run, value) {
  const id = String(value.id || `step-${run.steps.size + 1}`);
  let row = run.steps.get(id);
  if (!row) {
    const element = document.createElement('div');
    element.className = 'agent-run-step';
    const icon = document.createElement('i');
    const title = document.createElement('span');
    const tool = document.createElement('small');
    const error = document.createElement('p');
    error.className = 'agent-run-error';
    element.append(icon, title, tool, error);
    run.list.append(element);
    row = {element, icon, title, tool, error, status: 'pending'};
    run.steps.set(id, row);
  }
  row.status = normalizeRunStatus(value.status);
  row.element.className = `agent-run-step ${row.status}`;
  row.icon.textContent = row.status === 'complete' ? '✓' : row.status === 'failed' ? '!' : row.status === 'running' ? '●' : '·';
  row.title.textContent = value.title || row.title.textContent || '执行任务步骤';
  const toolMeta = [];
  if (value.tool) toolMeta.push(toolLabel(value.tool));
  if (value.toolSource) toolMeta.push(String(value.toolSource).toUpperCase());
  if (Number(value.latencyMs) > 0) toolMeta.push(`${Number(value.latencyMs)} ms`);
  row.tool.textContent = toolMeta.join(' · ');
  const errorText = [value.errorType, value.error].filter(Boolean).join('：');
  row.error.textContent = errorText ? `错误：${errorText}` : '';
  row.error.hidden = !errorText;
}

function normalizeRunStatus(status) {
  if (['complete', 'completed', 'success', 'done'].includes(String(status).toLowerCase())) return 'complete';
  if (['failed', 'error'].includes(String(status).toLowerCase())) return 'failed';
  if (['running', 'started', 'active', 'in_progress'].includes(String(status).toLowerCase())) return 'running';
  return 'pending';
}

function runningRunSummary(run) {
  const running = [...run.steps.values()].find((step) => step.status === 'running');
  return running ? running.title.textContent : '正在整理任务结果';
}

function completeAgentRun(run, data = {}) {
  [...run.steps.values()].forEach((step) => {
    if (step.status === 'running') {
      step.status = 'complete';
      step.element.className = 'agent-run-step complete';
      step.icon.textContent = '✓';
    }
  });
  const tools = Array.isArray(data.toolsUsed) ? data.toolsUsed : [];
  tools.forEach((tool) => run.tools.add(tool));
  const toolsCount = Number(data.agentRun?.toolCount) || run.tools.size;
  const sources = Number(data.agentRun?.sourceCount)
    || (Array.isArray(data.sources) ? data.sources.length : run.sourceCount);
  run.sourceCount = Math.max(run.sourceCount, sources);
  const completed = [...run.steps.values()].filter((step) => step.status === 'complete').length;
  const artifactCount = Number(data.agentRun?.artifactCount)
    || (Array.isArray(data.artifacts) ? data.artifacts.length : 0);
  run.summaryText.textContent = `已完成 ${completed} 个步骤 · ${toolsCount} 个工具 · ${run.sourceCount} 个来源`
    + (artifactCount ? ` · ${artifactCount} 个文件` : '');
  run.details.classList.add('complete');
  const artifacts = Array.isArray(data.artifacts) ? data.artifacts : [];
  if (artifacts.length) {
    const heading = document.createElement('strong');
    heading.textContent = '成果';
    run.outcomes.replaceChildren(heading, ...artifacts.map((artifact) => {
      const item = document.createElement('span');
      item.textContent = artifact.filename || artifact.displayName || '任务文件';
      return item;
    }));
    run.outcomes.hidden = false;
  }
  run.details.open = false;
}

function failAgentRun(run) {
  [...run.steps.values()].forEach((step) => {
    if (step.status === 'running') {
      step.status = 'failed';
      step.element.className = 'agent-run-step failed';
      step.icon.textContent = '!';
    }
  });
  run.summaryText.textContent = '任务未完成 · 请检查模型或服务配置';
  run.details.classList.add('failed');
}

function renderPersistedAgentRun(element, snapshot, metadata) {
  const agent = agentById(metadata.agentId);
  const skill = skillById(metadata.skillId);
  const run = createAgentRun(element, agent, skill);
  run.steps.clear();
  run.list.replaceChildren();
  const steps = Array.isArray(snapshot) ? snapshot : Array.isArray(snapshot.steps) ? snapshot.steps : [];
  steps.forEach((step, index) => upsertRunStep(run, {
    id: step.id || step.stepId || `step-${index + 1}`,
    title: step.title || step.label || step.message || step.name || `任务步骤 ${index + 1}`,
    status: step.status || 'complete',
    tool: step.tool || step.toolName || '',
    toolSource: step.toolSource,
    latencyMs: step.latencyMs,
    error: step.error || step.errorMessage,
    errorType: step.errorType
  }));
  const tools = Array.isArray(metadata.toolsUsed) ? metadata.toolsUsed : [];
  tools.forEach((tool) => run.tools.add(tool));
  run.sourceCount = Array.isArray(metadata.sources) ? metadata.sources.length : 0;
  completeAgentRun(run, metadata);
}

function parseJson(value, fallback) {
  if (value == null || value === '') return fallback;
  if (typeof value !== 'string') return value;
  try { return JSON.parse(value); } catch { return fallback; }
}

function addMessageMeta(element, data) {
  element.querySelector('.message-meta')?.remove();
  const sources = Array.isArray(data.sources) ? data.sources : [];
  const tools = Array.isArray(data.toolsUsed) ? data.toolsUsed : [];
  if (!sources.length && !tools.length) return;
  const meta = document.createElement('div');
  meta.className = 'message-meta';
  [...new Set(tools)].forEach((tool) => {
    const chip = document.createElement('span');
    chip.className = 'tool-chip';
    chip.textContent = toolLabel(tool);
    meta.append(chip);
  });
  if (!tools.length && sources.length) {
    const knowledgeChip = document.createElement('span');
    knowledgeChip.className = 'tool-chip';
    knowledgeChip.textContent = '文昌知识库';
    meta.append(knowledgeChip);
  }
  if (sources.length) meta.append(sourceDetails(sources.slice(0, 6)));
  element.append(meta);
}

function toolLabel(tool) {
  if (tool === 'webSearch') return '联网检索';
  if (tool === 'officialSourceSearch') return '权威来源';
  if (tool === 'knowledgeEvidence') return '文昌知识库';
  if (tool === 'placeSearch' || tool === 'searchStudyTourPlaces') return '地点查询';
  if (tool === 'policySearch') return '政策查询';
  if (tool === 'searchPublicServices') return '公共服务查询';
  if (tool === 'searchTownshipProfile') return '乡镇资料查询';
  if (tool === 'collectOfficialMaterials') return '专题资料采集';
  if (tool === 'createWenchangWordReport') return 'Word 报告生成';
  if (tool === 'exportWenchangData') return '数据表导出';
  if (tool === 'createStudyTourPackage') return '研学方案生成';
  if (tool === 'createPolicyBrief') return '政策简报生成';
  return '公共资源工具';
}

function sourceDetails(sources) {
  const details = document.createElement('details');
  details.className = 'source-details';
  const summary = document.createElement('summary');
  summary.textContent = `查看来源（${sources.length}）`;
  details.append(summary);
  const list = document.createElement('div');
  list.className = 'source-list';
  sources.forEach((source) => {
    const row = document.createElement('div');
    const title = document.createElement('strong');
    title.textContent = source.sourceOrganization || source.file || '文昌资料';
    const description = document.createElement('span');
    description.textContent = [source.section, source.sourceLevel].filter(Boolean).join(' · ') || '相关章节';
    row.append(title, description);
    if (/^https?:\/\//i.test(source.sourceUrl || '')) {
      const link = document.createElement('a');
      link.href = source.sourceUrl;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.textContent = '打开原始资料';
      row.append(link);
    }
    list.append(row);
  });
  details.append(list);
  return details;
}

function addArtifactCards(element, artifacts) {
  const items = Array.isArray(artifacts) ? artifacts : artifacts ? [artifacts] : [];
  items.filter((item) => item && (item.id || item.artifactId || item.filename)).forEach((artifact) => {
    const id = String(artifact.id || artifact.artifactId || artifact.filename);
    if (element.querySelector(`[data-artifact-id="${CSS.escape(id)}"]`)) return;
    let container = element.querySelector('.artifact-list');
    if (!container) {
      container = document.createElement('section');
      container.className = 'artifact-list';
      const title = document.createElement('strong');
      title.textContent = '已生成文件';
      container.append(title);
      element.append(container);
    }
    const card = document.createElement('div');
    card.className = 'artifact-card';
    card.dataset.artifactId = id;
    const icon = document.createElement('span');
    icon.className = 'artifact-file-icon';
    icon.textContent = artifactIcon(artifact);
    const copy = document.createElement('div');
    const filename = document.createElement('b');
    filename.className = 'artifact-name';
    filename.textContent = artifact.displayName || artifact.filename || '文昌智脑任务文件';
    const detail = document.createElement('small');
    detail.className = 'artifact-meta';
    const size = Number(artifact.sizeBytes || artifact.size) > 0 ? formatFileSize(artifact.sizeBytes || artifact.size) : '';
    detail.textContent = [artifactTypeLabel(artifact), size, Number(artifact.sourceCount) ? `${artifact.sourceCount} 个来源` : '']
      .filter(Boolean).join(' · ') || '任务成果';
    copy.append(filename, detail);
    const actions = document.createElement('div');
    const downloadUrl = safeArtifactUrl(artifact.downloadUrl)
      || (artifact.id || artifact.artifactId ? appUrl(`/api/artifacts/${encodeURIComponent(artifact.id || artifact.artifactId)}/download`) : '');
    if (downloadUrl) {
      const download = document.createElement('a');
      download.className = 'artifact-download';
      download.href = downloadUrl;
      download.download = artifact.filename || '';
      download.textContent = t('artifact.download');
      actions.append(download);
    }
    card.append(icon, copy, actions);
    container.append(card);
  });
}

function formatFileSize(value) {
  const bytes = Number(value) || 0;
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function artifactTypeLabel(artifact = {}) {
  const type = String(artifact.type || '').toUpperCase();
  if (type.includes('WORD')) return 'Word 文档';
  if (type === 'XLSX') return 'Excel 工作簿';
  if (type === 'CSV') return 'CSV 数据';
  if (type === 'PDF') return 'PDF 文档';
  return type || '任务文件';
}

async function refreshConversationArtifacts(conversationId, preferredElement = null) {
  if (!conversationId) return;
  try {
    const artifacts = await apiJson(`/api/artifacts?conversationId=${encodeURIComponent(conversationId)}`);
    if (!Array.isArray(artifacts) || !artifacts.length) return;
    if (preferredElement) {
      addArtifactCards(preferredElement, artifacts);
      return;
    }
    const assistants = [...messages.querySelectorAll('.message.assistant')];
    artifacts.forEach((artifact) => {
      const target = [...assistants].reverse().find((item) => artifact.skillId && item.dataset.skillId === artifact.skillId)
        || [...assistants].reverse().find((item) => artifact.createdByAgent && item.dataset.agentId === artifact.createdByAgent)
        || assistants.at(-1);
      if (target) addArtifactCards(target, [artifact]);
    });
  } catch {
    // Artifact API can be absent during startup; message rendering remains available.
  }
}

function artifactIcon(artifact) {
  const type = `${artifact.type || ''} ${artifact.filename || ''}`.toLowerCase();
  if (type.includes('xlsx') || type.includes('csv') || type.includes('excel')) return '▦';
  return '▤';
}

function safeArtifactUrl(value) {
  const url = String(value || '').trim();
  if (/^https?:\/\//i.test(url)) return url;
  return /^(\/(?!\/))/i.test(url) ? appUrl(url) : '';
}

function openAgentDetail(id, returnFocus = document.activeElement) {
  const agent = agentById(id);
  state.detailAgentId = agent.id;
  state.detailReturnFocus = returnFocus instanceof HTMLElement ? returnFocus : input;
  $('agentDetailTitle').textContent = agentName(agent);
  $('agentDetailTagline').textContent = agentDescription(agent);
  fillTextList($('agentDetailCapabilities'), agentCapabilities(agent));
  fillChips($('agentDetailInputs'), agentInputs(agent));
  fillChips($('agentDetailSkills'), agentSkills(agent).map((skillId) => skillById(skillId)?.command || skillId));
  fillChips($('agentDetailTools'), agentTools(agent).map(toolLabel));
  fillChips($('agentDetailArtifacts'), agent.artifactTypes);
  fillTextList($('agentDetailWorkflow'), agentWorkflow(agent));
  $('agentDetailApproval').textContent = agentApproval(agent);
  const examples = $('agentDetailExamples');
  examples.replaceChildren();
  agentExamples(agent).forEach((task) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = task;
    button.addEventListener('click', () => {
      selectAgent(agent.id);
      input.value = task;
      resizeInput();
      closeAgentDetail();
      input.focus();
    });
    examples.append(button);
  });
  if (!agentDetailDialog.open) agentDetailDialog.showModal();
  syncCommandState();
  window.setTimeout(() => $('agentDetailClose').focus(), 0);
}

function fillTextList(target, values = []) {
  target.replaceChildren(...(values || []).map((value) => {
    const item = document.createElement('li');
    item.textContent = value;
    return item;
  }));
}

function fillChips(target, values = []) {
  target.replaceChildren(...(values || []).map((value) => {
    const chip = document.createElement('span');
    chip.textContent = value;
    return chip;
  }));
}

function closeAgentDetail() {
  if (agentDetailDialog.open) agentDetailDialog.close();
  state.detailAgentId = null;
  syncCommandState();
  const returnFocus = state.detailReturnFocus;
  state.detailReturnFocus = null;
  if (returnFocus instanceof HTMLElement && returnFocus.isConnected) returnFocus.focus();
}

function startWithDetailedAgent() {
  const id = state.detailAgentId || 'wenchang';
  closeAgentDetail();
  selectAgent(id);
  input.focus();
}

function openSkillDetail(id, returnFocus = document.activeElement) {
  const skill = skillById(id);
  if (!skill) return;
  state.detailSkillId = skill.id;
  state.detailReturnFocus = returnFocus instanceof HTMLElement ? returnFocus : input;
  $('skillDetailTitle').textContent = skill.command;
  $('skillDetailDescription').textContent = skillDescription(skill);
  $('skillDetailInput').textContent = skillInputHint(skill);
  fillChips($('skillDetailOutput'), [skillOutput(skill), ...(skill.artifactTypes || [])]);
  $('skillDetailWorkflow').textContent = skillWorkflowLabel(skill.workflowType);
  fillChips($('skillDetailTools'), (skill.requiredTools || []).map(toolLabel));
  fillChips($('skillDetailCategories'), (skill.preferredCategories || []).length
    ? skill.preferredCategories.map(categoryLabel)
    : ['不限知识分类']);
  $('skillDetailApproval').textContent = skillApprovalLabel(skill.approvalPolicy);
  if (!skillDetailDialog.open) skillDetailDialog.showModal();
  syncCommandState();
  window.setTimeout(() => $('skillDetailClose').focus(), 0);
}

function closeSkillDetail() {
  if (skillDetailDialog.open) skillDetailDialog.close();
  state.detailSkillId = null;
  syncCommandState();
  const returnFocus = state.detailReturnFocus;
  state.detailReturnFocus = null;
  if (returnFocus instanceof HTMLElement && returnFocus.isConnected) returnFocus.focus();
  else input.focus();
}

function startWithDetailedSkill() {
  const id = state.detailSkillId;
  closeSkillDetail();
  if (id) selectSkill(id, {focus: true});
}

function skillWorkflowLabel(value = '') {
  const labels = {
    SINGLE_TOOL: '调用一个核心工具完成明确任务',
    EVIDENCE_REVIEW: '检索证据并进行来源一致性核验',
    POLICY_RESEARCH: '检索政策、核验官方来源并整理结果',
    STUDY_TOUR_PLANNING: '筛选真实地点、规划顺序并生成研学成果',
    DEEP_RESEARCH: '公开展示步骤，逐项检索、核验并综合来源',
    MCP_PUBLIC_SERVICE: '调用文昌公共资源 MCP 查询结构化服务数据'
  };
  return labels[value] || '根据任务调用所需工具并形成结果';
}

function skillApprovalLabel(value = '') {
  if (value === 'USER_REQUEST_CONFIRMS') return '你主动选择并发送任务即视为确认生成文件；长期资产变更仍需另行确认。';
  return '查询与整理可直接执行；涉及长期资产修改时会单独请求确认。';
}

function categoryLabel(value = '') {
  const labels = {
    current_topics: '近期动态', policy_planning: '政策规划', aerospace: '航天',
    tourism: '旅游研学', education_science: '教育科普', ecology: '生态',
    history: '历史文化', study_tour: '研学', public_services: '公共服务',
    population_administration: '人口与行政', administrative_unit: '乡镇资料'
  };
  return labels[value] || String(value).replaceAll('_', ' ');
}

function openApprovalDialog(data = {}) {
  state.pendingApproval = data;
  $('approvalOperation').textContent = data.operation || data.action || data.title || '执行智能体任务';
  $('approvalScope').textContent = data.impactScope || data.scope || '仅影响当前任务';
  $('approvalDescription').textContent = data.description || data.summary || '确认后，智能体将执行上述操作。';
  if (!approvalDialog.open) approvalDialog.showModal();
}

function closeApprovalDialog(clear = true) {
  if (approvalDialog.open) approvalDialog.close();
  if (clear) state.pendingApproval = null;
}

async function submitApprovalDecision(confirmed) {
  const approval = state.pendingApproval;
  if (!approval) return closeApprovalDialog();
  const id = approval.id || approval.approvalId;
  const supplied = confirmed ? approval.confirmUrl : approval.cancelUrl;
  const url = safeArtifactUrl(supplied)
    || (id ? `/api/agent/approvals/${encodeURIComponent(id)}/${confirmed ? 'confirm' : 'cancel'}` : '');
  if (!url) {
    showToast('暂时无法提交确认，请稍后重试');
    return;
  }
  $('approvalConfirm').disabled = true;
  $('approvalCancel').disabled = true;
  try {
    await apiJson(url, {method: 'POST'});
    closeApprovalDialog();
    showToast(confirmed ? '已确认执行' : '已取消操作');
  } catch (error) {
    showToast(error.message);
  } finally {
    $('approvalConfirm').disabled = false;
    $('approvalCancel').disabled = false;
  }
}

async function consumeSse(response, onEvent) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const {value, done} = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), {stream: !done}).replaceAll('\r\n', '\n');
    let boundary;
    while ((boundary = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      await dispatchSseBlock(block, onEvent);
    }
    if (done) break;
  }
  if (buffer.trim()) await dispatchSseBlock(buffer, onEvent);
}

async function dispatchSseBlock(block, onEvent) {
  let event = 'message';
  const dataLines = [];
  block.split('\n').forEach((line) => {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart());
  });
  if (!dataLines.length) return;
  const raw = dataLines.join('\n');
  let data;
  try { data = JSON.parse(raw); } catch { data = raw; }
  await onEvent(event, data);
}

function setChatBusy(busy) {
  state.busy = busy;
  sendButton.disabled = busy;
  input.disabled = busy;
  newChatButton.disabled = busy;
}

function setProgress(message) {
  progress.hidden = false;
  progressText.textContent = message;
}

function resizeInput() {
  input.style.height = 'auto';
  input.style.height = `${Math.min(input.scrollHeight, 170)}px`;
}

function scrollToLatest() {
  requestAnimationFrame(() => window.scrollTo({top: document.body.scrollHeight, behavior: 'smooth'}));
}

function dateGroup(value) {
  const date = new Date(value);
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const days = Math.round((start - target) / 86400000);
  if (days <= 0) return 'today';
  if (days === 1) return 'yesterday';
  if (days < 7) return 'recent';
  return 'older';
}

function openMobileSidebar() {
  if (!mobileSidebarQuery.matches) return;
  state.sidebarTrigger = document.activeElement;
  sidebar.inert = false;
  sidebar.setAttribute('aria-hidden', 'false');
  sidebar.classList.add('mobile-open');
  mobileOverlay.hidden = false;
  sidebarOpen.setAttribute('aria-expanded', 'true');
  localStorage.setItem('wenchang-sidebar-open', 'true');
  window.setTimeout(() => sidebarClose.focus(), 40);
}

function closeMobileSidebar(returnFocus = false) {
  sidebar.classList.remove('mobile-open');
  mobileOverlay.hidden = true;
  sidebarOpen.setAttribute('aria-expanded', 'false');
  const fallbackTarget = sidebar.contains(document.activeElement) ? sidebarOpen : null;
  const focusTarget = returnFocus ? state.sidebarTrigger : fallbackTarget;
  if (focusTarget instanceof HTMLElement) focusTarget.focus();
  if (mobileSidebarQuery.matches) {
    sidebar.inert = true;
    sidebar.setAttribute('aria-hidden', 'true');
  }
  localStorage.setItem('wenchang-sidebar-open', 'false');
}

function syncSidebarA11y() {
  if (mobileSidebarQuery.matches) {
    const open = sidebar.classList.contains('mobile-open');
    sidebar.inert = !open;
    sidebar.setAttribute('aria-hidden', String(!open));
    sidebarOpen.setAttribute('aria-expanded', String(open));
    return;
  }
  sidebar.classList.remove('mobile-open');
  sidebar.inert = false;
  sidebar.removeAttribute('aria-hidden');
  sidebarOpen.setAttribute('aria-expanded', 'false');
  mobileOverlay.hidden = true;
}

async function refreshKnowledgeStatus() {
  const pill = $('knowledgePill');
  try {
    const data = await apiJson('/api/knowledge/status');
    const ready = ['READY', 'LOADED'].includes(data.state);
    pill.className = ready ? 'ready' : 'error';
    const files = data.files ?? data.sourceFiles ?? 0;
    pill.querySelector('b').textContent = ready
      ? t('knowledge.summary', {files, chunks: data.chunks})
      : t('knowledge.notReady');
  } catch {
    pill.className = 'error';
    pill.querySelector('b').textContent = t('knowledge.failed');
  }
}

async function refreshModelStatus() {
  try {
    const status = await apiJson('/api/settings/model');
    state.model = status;
    updateModelUi(status);
    populateSettings(status);
  } catch {
    $('modelPill').className = 'error';
    $('modelPill').querySelector('b').textContent = t('model.statusUnavailable');
  }
}

function updateModelUi(status) {
  const remote = String(status.modelMode).startsWith('REMOTE');
  const modelName = String(status.model || '').trim() || providerName(status.provider);
  const configured = remote && status.configured === true && status.apiKeyConfigured === true;
  const label = configured ? `${providerName(status.provider)} · ${modelName}` : t('model.notConfigured');
  $('modelPill').className = configured ? 'ready' : 'error';
  $('modelPill').querySelector('b').textContent = label;
  $('sidebarModelLabel').textContent = configured ? modelName : t('model.openSettings');
  $('activeModelName').textContent = label;
  $('activeModelMode').textContent = configured ? t('model.current') : t('model.configureHint');
  $('mobileStatus').textContent = configured ? providerName(status.provider) : t('model.notConfigured');
}

async function refreshToolServiceStatus() {
  const card = document.querySelector('.tool-service-card');
  try {
    const catalog = await apiJson('/api/agent/tools');
    state.toolCatalog = catalog;
    const mcpTools = Array.isArray(catalog.mcpTools) ? catalog.mcpTools : [];
    const connected = mcpTools.length > 0;
    card.classList.toggle('connected', connected);
    $('toolServiceStatus').textContent = connected
      ? t('tools.connected', {count: mcpTools.length})
      : t('tools.disconnected');
    const labels = mcpTools.map((tool) => toolLabel(tool.name || tool)).filter(Boolean);
    if (labels.length) {
      $('toolServiceList').replaceChildren(...[...new Set(labels)].map((label) => {
        const chip = document.createElement('span');
        chip.textContent = label;
        return chip;
      }));
    }
  } catch {
    card.classList.remove('connected');
    $('toolServiceStatus').textContent = t('tools.statusUnavailable');
  }
}

function populateSettings(status) {
  $('providerInput').value = status.provider || 'deepseek';
  $('baseUrlInput').value = status.baseUrl || 'https://api.deepseek.com';
  $('modelInput').value = status.model || 'deepseek-chat';
  $('thinkingInput').checked = Boolean(status.thinkingEnabled);
  $('apiKeyMasked').textContent = status.apiKeyMasked || '尚未配置';
  apiKeyInput.value = '';
  apiKeyEditor.hidden = true;
}

function openSettings() {
  state.settingsTrigger = document.activeElement;
  drawerBackdrop.hidden = false;
  settingsDrawer.inert = false;
  settingsDrawer.classList.add('open');
  settingsDrawer.setAttribute('aria-hidden', 'false');
  $('settingsClose').focus();
  closeMobileSidebar(false);
  refreshModelStatus();
  refreshToolServiceStatus();
}

function closeSettings() {
  if (!settingsDrawer.classList.contains('open')) return;
  settingsDrawer.classList.remove('open');
  drawerBackdrop.hidden = true;
  const returnTarget = mobileSidebarQuery.matches ? sidebarOpen : state.settingsTrigger;
  if (returnTarget instanceof HTMLElement) returnTarget.focus();
  settingsDrawer.setAttribute('aria-hidden', 'true');
  settingsDrawer.inert = true;
}

function settingsPayload() {
  return {
    provider: $('providerInput').value,
    baseUrl: $('baseUrlInput').value.trim(),
    apiKey: apiKeyInput.value.trim(),
    model: $('modelInput').value.trim(),
    thinkingEnabled: $('thinkingInput').checked
  };
}

async function runAgentDiagnostics() {
  const button = $('runAgentDiagnosticsButton');
  button.disabled = true;
  button.textContent = `${t('common.checking')}…`;
  document.querySelectorAll('[data-diagnostic]').forEach((row) => {
    row.className = 'running';
    row.querySelector('b').textContent = t('common.checking');
    row.querySelector('small').textContent = '—';
  });
  try {
    const data = await apiJson('/api/admin/diagnostics/agent');
    updateDiagnostic('model', data.model?.connected, data.model?.latencyMs);
    updateDiagnostic('rag', data.rag?.ready, data.rag?.latencyMs);
    updateDiagnostic('webSearch', diagnosticAvailable(data.search?.webSearch), diagnosticLatency(data.search?.webSearch));
    updateDiagnostic('officialSearch', diagnosticAvailable(data.search?.officialSearch), diagnosticLatency(data.search?.officialSearch));
    updateDiagnostic('mcp', data.mcp?.connected, data.mcp?.latencyMs);
    updateDiagnostic('word', diagnosticAvailable(data.artifact?.word), diagnosticLatency(data.artifact?.word));
    updateDiagnostic('dataExport', diagnosticAvailable(data.artifact?.dataExport), diagnosticLatency(data.artifact?.dataExport));
  } catch (error) {
    document.querySelectorAll('[data-diagnostic]').forEach((row) => {
      row.className = 'failed';
      row.querySelector('b').textContent = t('common.error');
      row.querySelector('small').textContent = t('common.notCompleted');
    });
    showToast(error.message);
  } finally {
    button.disabled = false;
    button.textContent = t('common.again');
  }
}

function diagnosticAvailable(value) {
  if (typeof value === 'boolean') return value;
  if (value && typeof value === 'object') {
    return value.available === true || value.connected === true || value.ready === true
      || ['UP', 'PASS', 'AVAILABLE', 'SUCCESS'].includes(String(value.status || value.health || '').toUpperCase());
  }
  return ['UP', 'PASS', 'AVAILABLE', 'SUCCESS', '可用'].includes(String(value || '').toUpperCase());
}

function diagnosticLatency(value) {
  return value && typeof value === 'object' ? value.latencyMs : null;
}

function updateDiagnostic(name, available, latency) {
  const row = document.querySelector(`[data-diagnostic="${name}"]`);
  if (!row) return;
  row.className = available ? 'available' : 'failed';
  row.querySelector('b').textContent = available ? t('common.available') : t('common.error');
  row.querySelector('small').textContent = Number.isFinite(Number(latency)) ? `${Number(latency)} ms` : '—';
}

async function testConnection() {
  if (!settingsForm.reportValidity()) return;
  setSettingsBusy(true);
  resetTestResult();
  try {
  const response = await fetch(appUrl('/api/settings/model/test'), {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(settingsPayload())
    });
    const data = await response.json();
    showTestResult(Boolean(data.success), data.message || '连接测试完成');
  } catch (error) { showTestResult(false, error.message); }
  finally { setSettingsBusy(false); }
}

async function saveSettings(event) {
  event.preventDefault();
  if (!settingsForm.reportValidity()) return;
  setSettingsBusy(true);
  resetTestResult();
  try {
    const status = await apiJson('/api/settings/model', {
      method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(settingsPayload())
    });
    state.model = status;
    updateModelUi(status);
    populateSettings(status);
    showTestResult(true, `已切换到 ${providerName(status.provider)} · ${status.model}`);
    showToast('运行时模型已生效');
  } catch (error) { showTestResult(false, error.message); }
  finally { setSettingsBusy(false); }
}

async function restoreDefault() {
  setSettingsBusy(true);
  resetTestResult();
  try {
    const status = await apiJson('/api/settings/model/restore-default', {method: 'POST'});
    state.model = status;
    updateModelUi(status);
    populateSettings(status);
    const configured = String(status.modelMode).startsWith('REMOTE');
    showTestResult(configured, configured ? '已恢复 DeepSeek 服务配置' : '模型未配置，请填写 API Key');
    showToast('已恢复服务端默认配置');
  } catch (error) { showTestResult(false, error.message); }
  finally { setSettingsBusy(false); }
}

function setSettingsBusy(busy) {
  $('testConnectionButton').disabled = busy;
  $('saveSettingsButton').disabled = busy;
  $('restoreDefaultButton').disabled = busy;
}

function showTestResult(success, message) {
  testResult.hidden = false;
  testResult.className = `test-result ${success ? 'success' : 'error'}`;
  testResult.textContent = message;
}

function resetTestResult() {
  testResult.hidden = true;
  testResult.className = 'test-result';
  testResult.textContent = '';
}

function providerName(provider) {
  if (provider === 'deepseek') return 'DeepSeek';
  if (provider === 'local') return '模型未配置';
  return '兼容服务';
}

function modeName(mode) {
  if (mode === 'REMOTE_DEFAULT') return '服务端默认配置';
  if (mode === 'REMOTE_RUNTIME') return '浏览器运行时覆盖';
  return '模型未配置';
}

function showToast(message) {
  clearTimeout(toastTimer);
  toast.textContent = message;
  toast.hidden = false;
  toastTimer = setTimeout(() => { toast.hidden = true; }, 2800);
}

async function apiJson(url, options = {}) {
  const response = await fetch(appUrl(url), options);
  if (!response.ok) throw new Error(await readError(response));
  return response.status === 204 ? null : response.json();
}

async function readError(response) {
  try {
    const data = await response.json();
    return data.message || data.error || `请求失败（HTTP ${response.status}）`;
  } catch { return `请求失败（HTTP ${response.status}）`; }
}

initialize();
