const STORAGE_PROGRESS_KEY = "dinga_progress_v1";
const STORAGE_VOCAB_KEY = "dinga_vocab_v1";

const state = {
  meta: null,
  knowledge: { documents: [], chunks: [] },
  history: [],
  fontSize: 15.5,
  lowDataMode: false,
  audioRate: 1.0,
  totalSessionKb: 0,
  lastLatencyMs: 0,
  activeChallengePrompt: "",
  quiz: {
    questions: [],
    answered: {},
    correctCount: 0,
    totalAnswered: 0,
    streak: 0,
    viewMode: "focus", // "focus" | "list"
    focusIndex: 0
  },
  adaptive: {
    results: [],
    consecutiveCorrect: 0,
    consecutiveWrong: 0,
    pendingAction: null
  },
  simulator: {
    mode: "daily",
    currentStep: 1,
    totalSteps: 3,
    completed: false,
    stepLabel: "Yesterday",
    question: "",
    hint: "",
    exampleAnswer: "",
    history: [],
    lastReport: null
  },
  vocab: {
    items: [],
    reviewQueue: [],
    reviewIndex: 0,
    revealed: false,
    searchQuery: ""
  },
  placement: {
    step: 0,
    score: 0,
    answers: []
  },
  learnerState: {
    weakPoints: [],
    masteredTopics: [],
    recentTopics: [],
    quizAccuracyPercent: null
  }
};

const CEFR_LEVELS = ["A1", "A2", "B1", "B2"];

const LEVEL_SUGGESTED_QUESTIONS = {
  A1: [
    "Quero começar do zero (A1): quais são as primeiras frases em inglês para me apresentar e estudar tecnologia?",
    "Como não errar ao dizer minha idade em inglês? Por que 'I have 20 years' está errado?",
    "Monte minha trilha inicial A1 para perder o medo de ler inglês no computador."
  ],
  A2: [
    "Qual a diferença entre actually e currently? Me dê exemplos práticos.",
    "Corrija minha frase de daily: Yesterday I work in the API and I have 20 years.",
    "Quais são os falsos cognatos mais perigosos para brasileiros como pretend, library e push/pull?"
  ],
  B1: [
    "Como funciona grounding e RAG nos módulos de IA Generativa do Microsoft Learn?",
    "O que significam endpoint, payload, fallback e latency na documentação de uma API?",
    "Como usar skimming e scanning para ler a documentação técnica do Microsoft Learn mais rápido?"
  ],
  B2: [
    "Como estruturar uma atualização profissional completa de Daily Standup (Yesterday, Today, Blockers)?",
    "Como explicar nossa arquitetura híbrida de IA e governança ética MTG-IA em uma entrevista em inglês?",
    "Como a Dinga AI aplica LGPD, EU AI Act e os 3 pilares do MTG-IA?"
  ]
};

const el = {
  modePill: document.getElementById("mode-pill"),
  telemetryPill: document.getElementById("telemetry-pill"),
  stopAudioBtn: document.getElementById("stop-audio-btn"),
  toggleAudioSpeed: document.getElementById("toggle-audio-speed"),
  tabQuizCount: document.getElementById("tab-quiz-count"),
  tabVocabCount: document.getElementById("tab-vocab-count"),
  simModeDaily: document.getElementById("sim-mode-daily"),
  simModeInterview: document.getElementById("sim-mode-interview"),
  simRestartBtn: document.getElementById("sim-restart-btn"),
  simStepper: document.getElementById("sim-stepper"),
  simStageCard: document.getElementById("sim-stage-card"),
  simStepBadge: document.getElementById("sim-step-badge"),
  simSpeakQuestionBtn: document.getElementById("sim-speak-question-btn"),
  simQuestionText: document.getElementById("sim-question-text"),
  simHintText: document.getElementById("sim-hint-text"),
  simAnswerForm: document.getElementById("sim-answer-form"),
  simAnswerInput: document.getElementById("sim-answer-input"),
  simFillExampleBtn: document.getElementById("sim-fill-example-btn"),
  simSubmitBtn: document.getElementById("sim-submit-btn"),
  simFeedbackArea: document.getElementById("sim-feedback-area"),
  simReportArea: document.getElementById("sim-report-area"),
  vocabStatsText: document.getElementById("vocab-stats-text"),
  vocabSearchInput: document.getElementById("vocab-search-input"),
  vocabExportCsvBtn: document.getElementById("vocab-export-csv-btn"),
  vocabReviewBtn: document.getElementById("vocab-review-btn"),
  vocabFlashcardStage: document.getElementById("vocab-flashcard-stage"),
  vocabAddForm: document.getElementById("vocab-add-form"),
  vocabInputEn: document.getElementById("vocab-input-en"),
  vocabInputMeaning: document.getElementById("vocab-input-meaning"),
  vocabInputContext: document.getElementById("vocab-input-context"),
  vocabList: document.getElementById("vocab-list"),
  ragUploadDropzone: document.getElementById("rag-upload-dropzone"),
  ragFileInput: document.getElementById("rag-file-input"),
  refreshMetricsBtn: document.getElementById("refresh-metrics-btn"),
  metricsGrid: document.getElementById("metrics-grid"),
  metricsBreakdown: document.getElementById("metrics-breakdown"),
  activeLevelBadge: document.getElementById("active-level-badge"),
  openPlacementBtn: document.getElementById("open-placement-btn"),
  placementDialog: document.getElementById("placement-dialog"),
  placementStepPill: document.getElementById("placement-step-pill"),
  placementBody: document.getElementById("placement-body"),
  closePlacementBtn: document.getElementById("close-placement-btn"),
  adaptiveLevelBanner: document.getElementById("adaptive-level-banner"),
  adaptiveLevelText: document.getElementById("adaptive-level-text"),
  adaptiveLevelAccept: document.getElementById("adaptive-level-accept"),
  adaptiveLevelDismiss: document.getElementById("adaptive-level-dismiss"),
  level: document.getElementById("student-level"),
  goal: document.getElementById("student-goal"),
  skillSelect: document.getElementById("skill-select"),
  randomPromptBtn: document.getElementById("random-prompt-btn"),
  demoPrompts: document.getElementById("demo-prompts"),
  openQuizShortcut: document.getElementById("open-quiz-shortcut"),
  chatLog: document.getElementById("chat-log"),
  challengeBanner: document.getElementById("challenge-reply-banner"),
  challengeBannerText: document.getElementById("challenge-banner-text"),
  fillChallengeHint: document.getElementById("fill-challenge-hint"),
  cancelChallengeReply: document.getElementById("cancel-challenge-reply"),
  chatForm: document.getElementById("chat-form"),
  chatInput: document.getElementById("chat-input"),
  voiceInputBtn: document.getElementById("voice-input-btn"),
  suggestQuestionBtn: document.getElementById("suggest-question-btn"),
  memoryStatus: document.getElementById("memory-status"),
  sendBtn: document.getElementById("send-btn"),
  skillsGrid: document.getElementById("skills-grid"),
  fillSampleRag: document.getElementById("fill-sample-rag"),
  fillSampleRagGit: document.getElementById("fill-sample-rag-git"),
  fillSampleRagDaily: document.getElementById("fill-sample-rag-daily"),
  teacherRagForm: document.getElementById("teacher-rag-form"),
  ragDocTitle: document.getElementById("rag-doc-title"),
  ragDocLevel: document.getElementById("rag-doc-level"),
  ragDocCategory: document.getElementById("rag-doc-category"),
  ragDocSection: document.getElementById("rag-doc-section"),
  ragDocSource: document.getElementById("rag-doc-source"),
  ragDocContent: document.getElementById("rag-doc-content"),
  submitRagBtn: document.getElementById("submit-rag-btn"),
  teacherRagStatus: document.getElementById("teacher-rag-status"),
  docsCountLabel: document.getElementById("docs-count-label"),
  knowledgeList: document.getElementById("knowledge-list"),
  knowledgeFilter: document.getElementById("knowledge-filter"),
  feedbackList: document.getElementById("feedback-list"),
  quizGeneratorForm: document.getElementById("quiz-generator-form"),
  quizGenTopic: document.getElementById("quiz-gen-topic"),
  quizGenDoc: document.getElementById("quiz-gen-doc"),
  quizGenLevel: document.getElementById("quiz-gen-level"),
  quizGenCount: document.getElementById("quiz-gen-count"),
  quizGenBtn: document.getElementById("quiz-gen-btn"),
  quizGenStatus: document.getElementById("quiz-gen-status"),
  quizScorePill: document.getElementById("quiz-score-pill"),
  quizStreakPill: document.getElementById("quiz-streak-pill"),
  quizViewList: document.getElementById("quiz-view-list"),
  quizViewFocus: document.getElementById("quiz-view-focus"),
  quizResetBtn: document.getElementById("quiz-reset-btn"),
  quizProgressText: document.getElementById("quiz-progress-text"),
  quizAccuracyText: document.getElementById("quiz-accuracy-text"),
  quizProgressFill: document.getElementById("quiz-progress-fill"),
  quizAdaptiveBanner: document.getElementById("quiz-adaptive-banner"),
  quizAdaptiveText: document.getElementById("quiz-adaptive-text"),
  quizAdaptiveAccept: document.getElementById("quiz-adaptive-accept"),
  quizAdaptiveDismiss: document.getElementById("quiz-adaptive-dismiss"),
  quizCategoryFilter: document.getElementById("quiz-category-filter"),
  quizLevelFilter: document.getElementById("quiz-level-filter"),
  quizStepper: document.getElementById("quiz-stepper"),
  quizContainer: document.getElementById("quiz-container"),
  feedbackDialog: document.getElementById("feedback-dialog"),
  feedbackForm: document.getElementById("feedback-form"),
  feedbackWatermark: document.getElementById("feedback-watermark"),
  feedbackCategory: document.getElementById("feedback-category"),
  feedbackComment: document.getElementById("feedback-comment"),
  cancelFeedback: document.getElementById("cancel-feedback"),
  toggleLowData: document.getElementById("toggle-low-data"),
  toggleContrast: document.getElementById("toggle-contrast"),
  fontDec: document.getElementById("font-dec"),
  fontInc: document.getElementById("font-inc"),
  clearHistory: document.getElementById("clear-history"),
  exportSummary: document.getElementById("export-summary"),
  printReportBtn: document.getElementById("print-report-btn"),
  toastContainer: document.getElementById("toast-container")
};

function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function showToast(message) {
  if (!el.toastContainer) return;
  const toast = document.createElement("div");
  toast.className = "toast";
  toast.textContent = message;
  el.toastContainer.appendChild(toast);
  setTimeout(() => {
    toast.remove();
  }, 3000);
}

function renderMiniMarkdown(text) {
  const lines = escapeHtml(text).split("\n");
  let html = "";
  let inList = false;

  const formatInline = (line) =>
    line
      .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
      .replace(/\*(.+?)\*/g, "<em>$1</em>")
      .replace(/_(.+?)_/g, "<em>$1</em>")
      .replace(/`(.+?)`/g, "<code>$1</code>");

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) {
      if (inList) {
        html += "</ul>";
        inList = false;
      }
      continue;
    }
    if (line.startsWith("### ")) {
      if (inList) {
        html += "</ul>";
        inList = false;
      }
      html += `<h3>${formatInline(line.slice(4))}</h3>`;
    } else if (line.startsWith("- ") || line.startsWith("• ")) {
      if (!inList) {
        html += "<ul>";
        inList = true;
      }
      html += `<li>${formatInline(line.slice(2))}</li>`;
    } else {
      if (inList) {
        html += "</ul>";
        inList = false;
      }
      html += `<p>${formatInline(line)}</p>`;
    }
  }
  if (inList) {
    html += "</ul>";
  }
  return html;
}

function extractEnglishExamplesFallback(reply, sources) {
  const avoidRegex = /❌[^✅\n]*/g;
  const regex = /"([^"\n]{4,140})"|'([^'\n]{4,140})'/g;
  const ptChars = /[ãõçáéíóúâêôà]/i;
  const ptWords = /\b(nao|voce|atualmente|na verdade|livraria|pretender|parentes|atender|suportar|aguentar|tenho|anos|sugere|valida)\b/i;
  const enWords = /\b(i|you|he|she|it|we|they|am|is|are|was|were|do|does|did|have|has|will|would|could|should|can|must|the|and|but|while|before|after|under|through|every|more|than|without|in|on|at|to|for|with|from|into|yesterday|today|actually|currently|nowadays|pretend|intend|library|attend|support|parents|study|studied|worked|work|learning|student|students|question|information|feedback|blocker|endpoint|payload|grounding|hallucination|push|pull|daily|api|review|documentation|null|exception|error|branch|commit|avoid|remove|means|includes|supports|reduces|builds|turns|produce|beats|models|answers|sources|practice|safety)\b/i;

  const set = new Set();
  const scan = (txt) => {
    if (!txt) return;
    const sanitized = txt.replace(avoidRegex, "");
    let m;
    while ((m = regex.exec(sanitized)) !== null) {
      const candidate = (m[1] || m[2] || "").replace(/[*_`]/g, "").trim();
      if (!candidate) continue;
      const cleanCheck = candidate.replace(/São Paulo/g, "Sao Paulo");
      if (ptChars.test(cleanCheck) || ptWords.test(cleanCheck)) continue;
      if (enWords.test(cleanCheck)) {
        set.add(candidate);
      }
    }
  };
  scan(reply);
  (sources || []).forEach((s) => scan(s.content));
  if (set.size === 0) {
    set.add("Actually, I study technology every night.");
  }
  return Array.from(set).slice(0, 5);
}

function pushUniqueCapped(list, value, maxLen = 8) {
  const clean = String(value || "").replace(/\s+/g, " ").trim().slice(0, 80);
  if (!clean) return list;
  const filtered = list.filter((item) => item.toLowerCase() !== clean.toLowerCase());
  filtered.unshift(clean);
  return filtered.slice(0, maxLen);
}

function removeCaseInsensitive(list, value) {
  const norm = String(value || "").trim().toLowerCase();
  if (!norm) return list;
  return list.filter((item) => item.toLowerCase() !== norm);
}

function applyLearnerStateDelta(delta) {
  if (!delta || typeof delta !== "object") return;
  let changed = false;
  (delta.addedWeakPoints || []).forEach((wp) => {
    state.learnerState.weakPoints = pushUniqueCapped(state.learnerState.weakPoints, wp, 8);
    state.learnerState.masteredTopics = removeCaseInsensitive(state.learnerState.masteredTopics, wp);
    changed = true;
  });
  (delta.addedMasteredTopics || []).forEach((mt) => {
    state.learnerState.masteredTopics = pushUniqueCapped(state.learnerState.masteredTopics, mt, 8);
    state.learnerState.weakPoints = removeCaseInsensitive(state.learnerState.weakPoints, mt);
    changed = true;
  });
  if (delta.focusTopic) {
    state.learnerState.recentTopics = pushUniqueCapped(state.learnerState.recentTopics, delta.focusTopic, 6);
    changed = true;
  }
  if (changed) {
    saveLocalProgress();
    updateMemoryAndTelemetryBar();
  }
}

function recordLearnerQuizOutcome(category, isCorrect) {
  const topic = String(category || "").trim();
  if (topic) {
    state.learnerState.recentTopics = pushUniqueCapped(state.learnerState.recentTopics, topic, 6);
    if (isCorrect) {
      state.learnerState.masteredTopics = pushUniqueCapped(state.learnerState.masteredTopics, topic, 8);
      state.learnerState.weakPoints = removeCaseInsensitive(state.learnerState.weakPoints, topic);
    } else {
      state.learnerState.weakPoints = pushUniqueCapped(state.learnerState.weakPoints, topic, 8);
      state.learnerState.masteredTopics = removeCaseInsensitive(state.learnerState.masteredTopics, topic);
    }
  }
  if (state.quiz.totalAnswered > 0) {
    state.learnerState.quizAccuracyPercent = Math.round(
      (state.quiz.correctCount / state.quiz.totalAnswered) * 100
    );
  }
  updateMemoryAndTelemetryBar();
}

function recordLearnerSimulationOutcome(turnEval, mode) {
  if (!turnEval) return;
  const label = `${mode === "interview" ? "Entrevista" : "Daily"}: ${turnEval.stepLabel || "Etapa"}`;
  state.learnerState.recentTopics = pushUniqueCapped(state.learnerState.recentTopics, label, 6);
  if (Number(turnEval.grammarScore) < 75) {
    state.learnerState.weakPoints = pushUniqueCapped(state.learnerState.weakPoints, label, 8);
    state.learnerState.masteredTopics = removeCaseInsensitive(state.learnerState.masteredTopics, label);
  } else if (Number(turnEval.grammarScore) >= 85 && Number(turnEval.clarityScore) >= 80) {
    state.learnerState.masteredTopics = pushUniqueCapped(state.learnerState.masteredTopics, label, 8);
    state.learnerState.weakPoints = removeCaseInsensitive(state.learnerState.weakPoints, label);
  }
  saveLocalProgress();
  updateMemoryAndTelemetryBar();
}

function updateMemoryAndTelemetryBar() {
  if (el.memoryStatus) {
    const turns = state.history.length;
    const wpCount = state.learnerState.weakPoints.length;
    const mtCount = state.learnerState.masteredTopics.length;
    el.memoryStatus.textContent = turns
      ? `Sessão local (${turns} turno${turns > 1 ? "s" : ""} · LGPD)`
      : "Sessão local (0 turnos · LGPD)";
    el.memoryStatus.title = `Memória pedagógica local (LGPD): ${wpCount} ponto(s) a reforçar · ${mtCount} tópico(s) dominado(s). Apagada ao clicar em Limpar.`;
  }
  if (el.telemetryPill) {
    const modeTag = state.lowDataMode ? " · Leve" : "";
    el.telemetryPill.textContent = `${state.lastLatencyMs || "--"} ms · ${state.totalSessionKb.toFixed(2)} KB${modeTag}`;
  }
}

function saveLocalProgress() {
  try {
    const payload = {
      level: el.level?.value || "A2",
      goal: el.goal?.value || "Inglês para Tecnologia / Microsoft Learn",
      lowDataMode: state.lowDataMode,
      audioRate: state.audioRate,
      learnerState: state.learnerState,
      quiz: {
        answered: state.quiz.answered,
        correctCount: state.quiz.correctCount,
        totalAnswered: state.quiz.totalAnswered,
        streak: state.quiz.streak,
        viewMode: state.quiz.viewMode,
        focusIndex: state.quiz.focusIndex
      }
    };
    localStorage.setItem(STORAGE_PROGRESS_KEY, JSON.stringify(payload));
  } catch {
    // ignore storage quota or private mode restrictions
  }
}

function loadLocalProgress() {
  try {
    const raw = localStorage.getItem(STORAGE_PROGRESS_KEY);
    if (!raw) return;
    const saved = JSON.parse(raw);
    if (saved.level && CEFR_LEVELS.includes(saved.level)) {
      syncLevelUI(saved.level, true);
    }
    if (saved.goal && el.goal) {
      el.goal.value = saved.goal;
    }
    if (typeof saved.lowDataMode === "boolean") {
      state.lowDataMode = saved.lowDataMode;
      document.body.classList.toggle("low-data", state.lowDataMode);
      el.toggleLowData?.setAttribute("aria-pressed", String(state.lowDataMode));
    }
    if (saved.audioRate === 0.75 || saved.audioRate === 1.0) {
      state.audioRate = saved.audioRate;
      updateAudioSpeedUI();
    }
    if (saved.learnerState && typeof saved.learnerState === "object") {
      state.learnerState = {
        weakPoints: Array.isArray(saved.learnerState.weakPoints) ? saved.learnerState.weakPoints.slice(0, 8) : [],
        masteredTopics: Array.isArray(saved.learnerState.masteredTopics) ? saved.learnerState.masteredTopics.slice(0, 8) : [],
        recentTopics: Array.isArray(saved.learnerState.recentTopics) ? saved.learnerState.recentTopics.slice(0, 6) : [],
        quizAccuracyPercent: typeof saved.learnerState.quizAccuracyPercent === "number" ? saved.learnerState.quizAccuracyPercent : null
      };
    }
    if (saved.quiz && typeof saved.quiz === "object") {
      state.quiz.answered = saved.quiz.answered || {};
      state.quiz.correctCount = Number(saved.quiz.correctCount) || 0;
      state.quiz.totalAnswered = Number(saved.quiz.totalAnswered) || 0;
      state.quiz.streak = Number(saved.quiz.streak) || 0;
      if (saved.quiz.viewMode === "list" || saved.quiz.viewMode === "focus") {
        state.quiz.viewMode = saved.quiz.viewMode;
      }
      state.quiz.focusIndex = Math.max(0, Number(saved.quiz.focusIndex) || 0);
    }
  } catch {
    // ignore malformed storage
  }
}

function saveVocabToStorage() {
  try {
    localStorage.setItem(STORAGE_VOCAB_KEY, JSON.stringify(state.vocab.items));
  } catch {
    // ignore
  }
}

function loadVocabFromStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_VOCAB_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      state.vocab.items = parsed;
    }
  } catch {
    state.vocab.items = [];
  }
}

function updateAudioSpeedUI() {
  if (!el.toggleAudioSpeed) return;
  const isSlow = state.audioRate < 1.0;
  el.toggleAudioSpeed.textContent = isSlow ? "0.75x" : "1.0x";
  el.toggleAudioSpeed.setAttribute("aria-pressed", String(isSlow));
}

function syncLevelUI(newLevel, skipPersist = false) {
  if (el.level && el.level.value !== newLevel) {
    el.level.value = newLevel;
  }
  if (el.activeLevelBadge) {
    el.activeLevelBadge.textContent = `Nível ${newLevel}`;
  }
  if (el.quizGenLevel) {
    el.quizGenLevel.value = newLevel;
  }
  document.querySelectorAll(".level-pill-btn").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.level === newLevel);
  });
  if (state.adaptive.pendingAction && state.adaptive.pendingAction.targetLevel === newLevel) {
    state.adaptive.pendingAction = null;
    state.adaptive.consecutiveCorrect = 0;
    state.adaptive.consecutiveWrong = 0;
    renderAdaptiveBanners();
  }
  if (!skipPersist) {
    saveLocalProgress();
  }
}

function renderAdaptiveBanners() {
  const action = state.adaptive.pendingAction;
  const banners = [
    {
      box: el.adaptiveLevelBanner,
      text: el.adaptiveLevelText,
      accept: el.adaptiveLevelAccept
    },
    {
      box: el.quizAdaptiveBanner,
      text: el.quizAdaptiveText,
      accept: el.quizAdaptiveAccept
    }
  ];

  banners.forEach(({ box, text, accept }) => {
    if (!box) return;
    if (!action) {
      box.hidden = true;
      box.classList.remove("reinforce");
      return;
    }
    box.hidden = false;
    box.classList.toggle("reinforce", action.kind === "reinforce");
    if (text) text.textContent = action.message;
    if (accept) accept.textContent = action.buttonLabel;
  });
}

function recordAdaptiveOutcome(isCorrect) {
  state.adaptive.results.push(Boolean(isCorrect));
  if (state.adaptive.results.length > 8) {
    state.adaptive.results.shift();
  }
  if (isCorrect) {
    state.adaptive.consecutiveCorrect += 1;
    state.adaptive.consecutiveWrong = 0;
  } else {
    state.adaptive.consecutiveWrong += 1;
    state.adaptive.consecutiveCorrect = 0;
  }

  const currentLevel = el.level?.value || "A2";
  const levelIdx = CEFR_LEVELS.indexOf(currentLevel);
  const recent = state.adaptive.results;
  const hits = recent.filter(Boolean).length;
  const accuracy = recent.length > 0 ? hits / recent.length : 0;

  if (
    levelIdx >= 0 &&
    levelIdx < CEFR_LEVELS.length - 1 &&
    (state.adaptive.consecutiveCorrect >= 3 || (recent.length >= 4 && accuracy >= 0.8))
  ) {
    const nextLevel = CEFR_LEVELS[levelIdx + 1];
    state.adaptive.pendingAction = {
      kind: "upgrade",
      targetLevel: nextLevel,
      message: `Ótimo desempenho! Pronto para avançar para ${nextLevel}?`,
      buttonLabel: `Subir para ${nextLevel}`
    };
    renderAdaptiveBanners();
    return;
  }

  if (state.adaptive.consecutiveWrong >= 2 || (recent.length >= 4 && accuracy <= 0.4)) {
    if (levelIdx > 0) {
      const prevLevel = CEFR_LEVELS[levelIdx - 1];
      state.adaptive.pendingAction = {
        kind: "reinforce",
        targetLevel: prevLevel,
        message: `Alguns ajustes seguidos — quer reforçar no nível ${prevLevel}?`,
        buttonLabel: `Reforçar ${prevLevel}`
      };
    } else {
      state.adaptive.pendingAction = {
        kind: "reinforce",
        targetLevel: "A1",
        practiceBase: true,
        message: "Vamos reforçar a base A1 com uma prática guiada?",
        buttonLabel: "Praticar A1"
      };
    }
    renderAdaptiveBanners();
  }
}

function applyAdaptiveAction() {
  const action = state.adaptive.pendingAction;
  if (!action) return;
  syncLevelUI(action.targetLevel);
  state.adaptive.results = [];
  state.adaptive.consecutiveCorrect = 0;
  state.adaptive.consecutiveWrong = 0;
  state.adaptive.pendingAction = null;
  renderAdaptiveBanners();

  if (action.practiceBase) {
    activateTab("tutor");
    el.skillSelect.value = "pratica-ativa";
    submitChat("Quero reforçar a base A1 com um exercício guiado simples de apresentação.");
  } else {
    showToast(`Nível atualizado para ${action.targetLevel}.`);
  }
}

function dismissAdaptiveSuggestion() {
  state.adaptive.consecutiveCorrect = 0;
  state.adaptive.consecutiveWrong = 0;
  state.adaptive.pendingAction = null;
  renderAdaptiveBanners();
}

function detectMicroChallengeOutcome(replyText) {
  if (!replyText || !replyText.includes("Avaliação do seu Micro-desafio")) {
    return null;
  }
  if (
    replyText.includes("Excelente!") ||
    replyText.includes("Muito bem!") ||
    replyText.includes("Resposta certeira!") ||
    replyText.includes("Parabéns")
  ) {
    return true;
  }
  if (
    replyText.includes("Quase lá!") ||
    replyText.includes("Ajuste apenas") ||
    replyText.includes("Correção gramatical")
  ) {
    return false;
  }
  return null;
}

function setupTabs() {
  const buttons = document.querySelectorAll(".tab-btn");
  const panels = document.querySelectorAll(".tab-panel");
  buttons.forEach((btn) => {
    btn.addEventListener("click", () => activateTab(btn.dataset.tab, buttons, panels));
  });
}

function activateTab(tabName, buttons = document.querySelectorAll(".tab-btn"), panels = document.querySelectorAll(".tab-panel")) {
  buttons.forEach((b) => {
    const active = b.dataset.tab === tabName;
    b.classList.toggle("active", active);
    b.setAttribute("aria-selected", String(active));
  });
  panels.forEach((p) => {
    const active = p.id === `tab-${tabName}`;
    p.classList.toggle("active", active);
    p.hidden = !active;
  });
  if (tabName === "architecture") {
    refreshSessionMetrics();
  } else if (tabName === "simulator" && !state.simulator.question) {
    startSimulation(state.simulator.mode);
  } else if (tabName === "vocab") {
    renderVocab();
  }
}

function suggestQuestionForCurrentLevel(autoSend = false) {
  const lvl = el.level?.value || "A2";
  const pool = LEVEL_SUGGESTED_QUESTIONS[lvl] || LEVEL_SUGGESTED_QUESTIONS.A2;
  const pick = pool[Math.floor(Math.random() * pool.length)];
  if (autoSend) {
    submitChat(pick);
  } else {
    el.chatInput.value = pick;
    el.chatInput.focus();
  }
}

function buildStarterHintForChallenge(promptText) {
  const norm = (promptText || "").toLowerCase();
  if (norm.includes("actually") || norm.includes("na verdade") || norm.includes("falso cognato")) {
    return "Actually, I study technology every night.";
  }
  if (norm.includes("yesterday") || norm.includes("daily")) {
    return "Yesterday I worked on the backend API, and today I will write unit tests.";
  }
  if (norm.includes("grounding") || norm.includes("rag") || norm.includes("endpoint") || norm.includes("api")) {
    return "We use RAG to ground the AI responses in trusted documentation.";
  }
  if (norm.includes("idade") || norm.includes("apresent")) {
    return "Hello! My name is Alex, I am 20 years old, and I study technology.";
  }
  return "Actually, I am practicing English with curated technical documentation.";
}

function setupVoiceRecognition() {
  if (!el.voiceInputBtn) return;
  const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRec) {
    el.voiceInputBtn.addEventListener("click", () => {
      showToast("Reconhecimento de voz não disponível neste navegador.");
    });
    return;
  }

  let recognition = null;
  let listening = false;

  el.voiceInputBtn.addEventListener("click", () => {
    if (listening && recognition) {
      recognition.stop();
      return;
    }
    recognition = new SpeechRec();
    recognition.lang = el.challengeBanner && !el.challengeBanner.hidden ? "en-US" : "pt-BR";
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;

    recognition.onstart = () => {
      listening = true;
      el.voiceInputBtn.classList.add("active");
      el.voiceInputBtn.textContent = "Ouvindo...";
    };

    recognition.onresult = (event) => {
      const transcript = event.results?.[0]?.[0]?.transcript || "";
      if (transcript) {
        el.chatInput.value = (el.chatInput.value ? el.chatInput.value + " " : "") + transcript;
        el.chatInput.focus();
      }
    };

    recognition.onerror = () => {
      showToast("Não foi possível capturar o áudio.");
    };

    recognition.onend = () => {
      listening = false;
      el.voiceInputBtn.classList.remove("active");
      el.voiceInputBtn.textContent = "Voz";
    };

    recognition.start();
  });
}

function setupA11yAndShortcuts() {
  document.querySelectorAll(".level-pill-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      syncLevelUI(btn.dataset.level);
    });
  });

  el.level?.addEventListener("change", () => syncLevelUI(el.level.value));
  el.goal?.addEventListener("change", () => saveLocalProgress());

  el.toggleAudioSpeed?.addEventListener("click", () => {
    state.audioRate = state.audioRate < 1.0 ? 1.0 : 0.75;
    updateAudioSpeedUI();
    saveLocalProgress();
    showToast(`Velocidade de pronúncia: ${state.audioRate.toFixed(2).replace(/0$/, "")}x`);
  });

  el.adaptiveLevelAccept?.addEventListener("click", applyAdaptiveAction);
  el.adaptiveLevelDismiss?.addEventListener("click", dismissAdaptiveSuggestion);
  el.quizAdaptiveAccept?.addEventListener("click", applyAdaptiveAction);
  el.quizAdaptiveDismiss?.addEventListener("click", dismissAdaptiveSuggestion);

  el.randomPromptBtn?.addEventListener("click", (e) => {
    e.preventDefault();
    e.stopPropagation();
    suggestQuestionForCurrentLevel(true);
  });
  el.suggestQuestionBtn?.addEventListener("click", () => suggestQuestionForCurrentLevel(false));

  el.chatInput?.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      submitChat(el.chatInput.value);
    }
  });

  el.stopAudioBtn?.addEventListener("click", () => {
    stopAllSpeech();
  });

  el.toggleLowData.addEventListener("click", () => {
    state.lowDataMode = !state.lowDataMode;
    document.body.classList.toggle("low-data", state.lowDataMode);
    el.toggleLowData.setAttribute("aria-pressed", String(state.lowDataMode));
    updateMemoryAndTelemetryBar();
    saveLocalProgress();
  });

  el.toggleContrast.addEventListener("click", () => {
    const enabled = document.body.classList.toggle("high-contrast");
    el.toggleContrast.setAttribute("aria-pressed", String(enabled));
  });

  el.fontDec.addEventListener("click", () => {
    state.fontSize = Math.max(13, state.fontSize - 1);
    document.documentElement.style.setProperty("--font-size", `${state.fontSize}px`);
  });

  el.fontInc.addEventListener("click", () => {
    state.fontSize = Math.min(21, state.fontSize + 1);
    document.documentElement.style.setProperty("--font-size", `${state.fontSize}px`);
  });

  el.clearHistory.addEventListener("click", () => {
    stopAllSpeech();
    try {
      localStorage.removeItem(STORAGE_PROGRESS_KEY);
      localStorage.removeItem(STORAGE_VOCAB_KEY);
    } catch {
      // ignore
    }
    state.history = [];
    state.totalSessionKb = 0;
    state.lastLatencyMs = 0;
    state.lowDataMode = false;
    state.audioRate = 1.0;
    document.body.classList.remove("low-data");
    el.toggleLowData?.setAttribute("aria-pressed", "false");
    updateAudioSpeedUI();
    syncLevelUI("A2", true);
    if (el.goal) el.goal.selectedIndex = 0;
    state.quiz.answered = {};
    state.quiz.correctCount = 0;
    state.quiz.totalAnswered = 0;
    state.quiz.streak = 0;
    state.quiz.focusIndex = 0;
    state.simulator = {
      mode: "daily",
      currentStep: 1,
      totalSteps: 3,
      completed: false,
      stepLabel: "Yesterday",
      question: "",
      hint: "",
      exampleAnswer: "",
      history: [],
      lastReport: null
    };
    if (el.simFeedbackArea) {
      el.simFeedbackArea.hidden = true;
      el.simFeedbackArea.innerHTML = "";
    }
    if (el.simReportArea) {
      el.simReportArea.hidden = true;
      el.simReportArea.innerHTML = "";
    }
    if (el.simAnswerInput) el.simAnswerInput.value = "";
    if (el.simStageCard) el.simStageCard.hidden = false;
    state.vocab.items = [];
    state.vocab.reviewQueue = [];
    state.vocab.reviewIndex = 0;
    state.vocab.revealed = false;
    state.vocab.searchQuery = "";
    if (el.vocabSearchInput) el.vocabSearchInput.value = "";
    state.adaptive = { results: [], consecutiveCorrect: 0, consecutiveWrong: 0, pendingAction: null };
    state.learnerState = { weakPoints: [], masteredTopics: [], recentTopics: [], quizAccuracyPercent: null };
    renderAdaptiveBanners();
    hideChallengeReplyBanner();
    updateMemoryAndTelemetryBar();
    renderQuiz();
    renderVocab();
    renderWelcomeMessage();
    showToast("Histórico, progresso e flashcards apagados (LGPD).");
  });

  el.exportSummary.addEventListener("click", exportSummaryForTeacher);
  el.printReportBtn?.addEventListener("click", printSessionReport);
  el.openPlacementBtn?.addEventListener("click", openPlacementTest);
  el.closePlacementBtn?.addEventListener("click", () => el.placementDialog?.close());
  el.openQuizShortcut?.addEventListener("click", () => activateTab("quiz"));

  el.cancelChallengeReply?.addEventListener("click", hideChallengeReplyBanner);
  el.fillChallengeHint?.addEventListener("click", () => {
    el.chatInput.value = buildStarterHintForChallenge(state.activeChallengePrompt);
    el.chatInput.focus();
  });

  setupVoiceRecognition();
  setupGlobalKeyboardShortcuts();
}

function getActiveTabName() {
  const activeBtn = document.querySelector(".tab-btn.active");
  return activeBtn?.dataset?.tab || "tutor";
}

function isTypingTarget(target) {
  if (!target) return false;
  const tag = (target.tagName || "").toUpperCase();
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable;
}

function setupGlobalKeyboardShortcuts() {
  document.addEventListener("keydown", (e) => {
    // Ctrl+K / Cmd+K: foca o campo de pergunta no Tutor IA
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
      e.preventDefault();
      if (getActiveTabName() !== "tutor") {
        activateTab("tutor");
      }
      el.chatInput?.focus();
      return;
    }

    // Esc: interrompe a leitura em voz alta se ativa
    if (e.key === "Escape") {
      if (el.stopAudioBtn && !el.stopAudioBtn.hidden) {
        stopAllSpeech();
      }
      return;
    }

    // Não intercepta teclas simples quando o usuário está digitando ou com modal aberto
    if (isTypingTarget(e.target) || document.querySelector("dialog[open]")) {
      return;
    }

    const activeTab = getActiveTabName();

    // 1-4 no Quiz: seleciona alternativa da questão em foco (ou primeira não respondida)
    if (activeTab === "quiz" && !e.ctrlKey && !e.metaKey && !e.altKey && ["1", "2", "3", "4"].includes(e.key)) {
      const optIdx = Number(e.key) - 1;
      const cards = el.quizContainer?.querySelectorAll(".quiz-card") || [];
      for (const card of cards) {
        const btn = card.querySelector(`.quiz-option-btn[data-opt-idx="${optIdx}"]:not(:disabled)`);
        if (btn) {
          e.preventDefault();
          btn.click();
          break;
        }
      }
      return;
    }

    // Espaço no Vocabulário: vira (ou desvira) o Flashcard SRS ativo
    if (
      activeTab === "vocab" &&
      (e.code === "Space" || e.key === " ") &&
      el.vocabFlashcardStage &&
      !el.vocabFlashcardStage.hidden &&
      state.vocab.reviewIndex < state.vocab.reviewQueue.length
    ) {
      e.preventDefault();
      state.vocab.revealed = !state.vocab.revealed;
      renderVocabFlashcard();
    }
  });
}

function showChallengeReplyBanner(practicePrompt) {
  if (!el.challengeBanner) return;
  state.activeChallengePrompt = practicePrompt;
  el.challengeBannerText.textContent = practicePrompt;
  el.challengeBanner.hidden = false;
  el.chatInput.placeholder = `Responda em inglês: "${practicePrompt}"`;
  el.chatInput.focus();
}

function hideChallengeReplyBanner() {
  if (!el.challengeBanner) return;
  state.activeChallengePrompt = "";
  el.challengeBanner.hidden = true;
  el.chatInput.placeholder = "Digite sua dúvida ou pratique uma frase em inglês...";
}

function renderWelcomeMessage() {
  el.chatLog.innerHTML = `
    <div class="msg msg-ai">
      <div class="msg-header">
        <span class="skill-badge">Dinga AI</span>
      </div>
      <div class="msg-body">
        <p>Olá! Como posso ajudar no seu inglês hoje? Escolha um tópico ou digite abaixo:</p>
        <div class="welcome-grid">
          <button type="button" class="welcome-action-card" data-welcome-prompt="Como funciona grounding e RAG nos módulos de IA do Microsoft Learn?">
            Inglês do Microsoft Learn (RAG)
          </button>
          <button type="button" class="welcome-action-card" data-welcome-prompt="Qual a diferença entre actually e currently? Me dê exemplos práticos.">
            Falsos Cognatos (Actually vs Currently)
          </button>
          <button type="button" class="welcome-action-card" data-welcome-prompt="Corrija minha frase de daily: Yesterday I work in the API and I have 20 years.">
            Corrigir frase de Daily
          </button>
          <button type="button" class="welcome-action-card" data-welcome-quiz="true">
            Praticar no Quiz &rarr;
          </button>
        </div>
      </div>
    </div>
  `;

  el.chatLog.querySelectorAll("[data-welcome-prompt]").forEach((btn) => {
    btn.addEventListener("click", () => {
      el.skillSelect.value = "auto";
      submitChat(btn.dataset.welcomePrompt);
    });
  });

  el.chatLog.querySelector("[data-welcome-quiz]")?.addEventListener("click", () => {
    activateTab("quiz");
  });
}

function appendUserMessage(text) {
  const node = document.createElement("div");
  node.className = "msg msg-user";
  node.innerHTML = `<div class="msg-body"><p>${escapeHtml(text)}</p></div>`;
  el.chatLog.appendChild(node);
  el.chatLog.scrollTop = el.chatLog.scrollHeight;
}

function appendLoadingIndicator() {
  const node = document.createElement("div");
  node.className = "msg msg-ai msg-loading";
  node.innerHTML = `
    <div class="typing-dots" aria-hidden="true"><span></span><span></span><span></span></div>
    <span>Consultando base...</span>
  `;
  el.chatLog.appendChild(node);
  el.chatLog.scrollTop = el.chatLog.scrollHeight;
  return node;
}

function appendAiResponse(data, clientLatencyMs) {
  const node = document.createElement("article");
  node.className = "msg msg-ai";

  const englishExamples =
    data.englishExamples && data.englishExamples.length
      ? data.englishExamples
      : extractEnglishExamplesFallback(data.reply, data.retrievedSources);

  const englishChipsHtml = englishExamples
    .map((phrase) => {
      const saved = isVocabSaved(phrase);
      return `
        <span class="english-chip-group">
          <button class="english-chip" type="button" data-en="${escapeHtml(phrase)}" title="Ouvir em Inglês (en-US)">🔊 "${escapeHtml(phrase)}"</button>
          <button class="vocab-save-mini-btn${saved ? " saved" : ""}" type="button" data-save-en="${escapeHtml(phrase)}" title="Salvar no Caderno de Vocabulário (SRS)">${saved ? "✓" : "+"}</button>
        </span>`;
    })
    .join("");

  const sourcesHtml =
    data.retrievedSources && data.retrievedSources.length
      ? data.retrievedSources
          .map(
            (src) => `
          <div class="rag-chunk">
            <div class="brand-title-row" style="justify-content:space-between">
              <div>
                <strong>${escapeHtml(src.title)}</strong> · ${escapeHtml(src.section)}
                <span class="pill">${escapeHtml(src.level)}</span>
              </div>
              <button class="btn-chip btn-xs gen-quiz-from-chunk-btn" type="button" data-doc-id="${escapeHtml(src.docId)}" data-level="${escapeHtml(src.level)}">
                + Questão Quiz
              </button>
            </div>
            <div>${renderMiniMarkdown(src.content)}</div>
          </div>`
          )
          .join("")
      : `<p class="muted small">Nenhuma fonte específica necessária.</p>`;

  const serverMs = data.mtg.responseTimeMs ?? clientLatencyMs ?? 2;
  const payloadKb = Number(data.mtg.payloadSizeKb ?? 1.2).toFixed(2);
  const groundedPct =
    typeof data.mtg?.groundednessScore === "number" && data.mtg.sourcesCount > 0
      ? Math.round(data.mtg.groundednessScore * 100)
      : null;
  const groundedTag = groundedPct !== null ? ` · Ancoragem ${groundedPct}%` : "";
  const guardrailBadges = [
    data.mtg?.piiMasked ? `<span class="pill pill-warn" title="Dados pessoais mascarados automaticamente (LGPD)">LGPD Ativa</span>` : "",
    data.mtg?.antiPlagiarismTriggered ? `<span class="pill pill-warn" title="Redirecionamento socrático anti-plágio (MTG-IA)">Anti-Plágio</span>` : ""
  ]
    .filter(Boolean)
    .join("");

  node.innerHTML = `
    <div class="msg-header">
      <div class="brand-title-row">
        <span class="skill-badge">${escapeHtml(data.activeSkill.name)}</span>
        ${guardrailBadges}
      </div>
      <div class="mtg-actions">
        <button class="btn-chip btn-xs speak-btn" type="button" title="Ouvir explicação em Português">🔊 PT</button>
        <button class="btn-chip btn-xs speak-en-btn" type="button" title="Ouvir exemplos em Inglês">🇺🇸 EN</button>
      </div>
    </div>
    <div class="msg-body">${renderMiniMarkdown(data.reply)}</div>
    <div class="english-examples-box">
      <span class="english-examples-label">Pronúncia:</span>
      ${englishChipsHtml}
    </div>
    <div class="practice-box">
      <div style="flex:1">
        <strong>Prática:</strong> ${escapeHtml(data.practicePrompt)}
      </div>
      <div class="practice-box-actions">
        <button class="btn-chip btn-xs hint-challenge-btn" type="button">Dica</button>
        <button class="btn-primary btn-xs reply-challenge-btn" type="button">Responder</button>
      </div>
    </div>
    <details class="rag-details">
      <summary>Fontes RAG (${data.mtg.sourcesCount})</summary>
      ${sourcesHtml}
    </details>
    <div class="mtg-footer">
      <span>${escapeHtml(data.mtg.executionMode)} · <code>${escapeHtml(data.mtg.watermarkId)}</code> · ${escapeHtml(serverMs)} ms · ${escapeHtml(payloadKb)} KB${escapeHtml(groundedTag)}</span>
      <button class="btn-ghost btn-xs report-btn" type="button">Reportar</button>
    </div>
  `;

  node.querySelector(".speak-btn").addEventListener("click", (e) => speakPortuguese(data.reply, e.currentTarget));
  node.querySelector(".speak-en-btn").addEventListener("click", (e) => speakEnglishExamples(englishExamples, e.currentTarget));
  node.querySelectorAll(".english-chip").forEach((chip) => {
    chip.addEventListener("click", (e) => speakEnglishExamples([chip.dataset.en], e.currentTarget));
  });
  node.querySelectorAll(".vocab-save-mini-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const phrase = btn.dataset.saveEn;
      const contextLabel = `${data.activeSkill?.name || "Tutor IA"} · Nível ${el.level?.value || "A2"}`;
      const meaningHint = data.retrievedSources?.[0]?.title
        ? `Referência: ${data.retrievedSources[0].title} (${data.retrievedSources[0].section})`
        : "Expressão salva da prática com o Tutor Dinga AI.";
      const added = addVocabItem(phrase, meaningHint, contextLabel);
      btn.classList.add("saved");
      btn.textContent = "✓";
      showToast(added ? "Salvo no Caderno de Vocabulário (SRS)." : "Termo já estava no Caderno.");
    });
  });
  node.querySelector(".reply-challenge-btn").addEventListener("click", () => {
    showChallengeReplyBanner(data.practicePrompt);
  });
  node.querySelector(".hint-challenge-btn").addEventListener("click", () => {
    showChallengeReplyBanner(data.practicePrompt);
    el.chatInput.value = buildStarterHintForChallenge(data.practicePrompt);
    el.chatInput.focus();
  });
  node.querySelectorAll(".gen-quiz-from-chunk-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      await triggerQuickQuizGeneration({
        docId: btn.dataset.docId,
        level: btn.dataset.level === "ALL" ? el.level.value : btn.dataset.level,
        topic: "",
        count: 1
      });
    });
  });
  node.querySelector(".report-btn").addEventListener("click", () => openFeedbackModal(data.mtg.watermarkId));

  el.chatLog.appendChild(node);
  el.chatLog.scrollTop = el.chatLog.scrollHeight;
}

const audioState = {
  element: null,
  objectUrl: null,
  requestSeq: 0,
  abortController: null,
  activeButton: null
};

function clearActiveAudioButton() {
  if (audioState.activeButton) {
    audioState.activeButton.classList.remove("audio-loading", "audio-playing");
    audioState.activeButton = null;
  }
}

function setActiveAudioButtonState(btn, mode) {
  clearActiveAudioButton();
  if (!btn) return;
  audioState.activeButton = btn;
  if (mode === "loading") {
    btn.classList.add("audio-loading");
  } else if (mode === "playing") {
    btn.classList.add("audio-playing");
  }
}

function selectVoiceForLang(langPrefix) {
  if (!("speechSynthesis" in window)) return null;
  const voices = window.speechSynthesis.getVoices() || [];
  return (
    voices.find((v) => v.lang && v.lang.toLowerCase().startsWith(langPrefix.toLowerCase())) || null
  );
}

function setAudioActiveState(active) {
  if (el.stopAudioBtn) {
    el.stopAudioBtn.hidden = !active;
  }
  if (!active) {
    clearActiveAudioButton();
  }
}

function releaseCurrentAudio() {
  if (audioState.abortController) {
    audioState.abortController.abort();
    audioState.abortController = null;
  }
  if (audioState.element) {
    try {
      audioState.element.pause();
      audioState.element.removeAttribute("src");
      audioState.element.load();
    } catch {
      // ignore
    }
    audioState.element = null;
  }
  if (audioState.objectUrl) {
    URL.revokeObjectURL(audioState.objectUrl);
    audioState.objectUrl = null;
  }
}

function stopAllSpeech() {
  audioState.requestSeq += 1;
  releaseCurrentAudio();
  if ("speechSynthesis" in window) {
    window.speechSynthesis.cancel();
  }
  setAudioActiveState(false);
}

function speakViaBrowserFallback(cleanText, lang, rate, seq, triggerBtn = null) {
  if (!("speechSynthesis" in window)) {
    return false;
  }
  const voices = window.speechSynthesis.getVoices() || [];
  if (!voices.length) {
    return false;
  }
  const voice = selectVoiceForLang(lang) || selectVoiceForLang(lang.split("-")[0]);
  const utterance = new SpeechSynthesisUtterance(cleanText);
  utterance.lang = lang;
  if (voice) utterance.voice = voice;
  utterance.rate = rate;
  utterance.onstart = () => {
    if (audioState.requestSeq === seq) {
      setAudioActiveState(true);
      setActiveAudioButtonState(triggerBtn, "playing");
    }
  };
  utterance.onend = () => {
    if (audioState.requestSeq === seq) setAudioActiveState(false);
  };
  utterance.onerror = () => {
    if (audioState.requestSeq === seq) {
      setAudioActiveState(false);
      showToast("Não foi possível reproduzir o áudio neste navegador.");
    }
  };
  window.speechSynthesis.speak(utterance);
  return true;
}

async function playTtsFromBackend(text, lang, baseRate = 1.0, triggerBtn = null) {
  const clean = String(text || "").replace(/[#*`_]/g, " ").replace(/\s+/g, " ").trim();
  if (!clean) return;

  const effectiveRate = state.audioRate < 1.0 ? 0.75 : baseRate;

  stopAllSpeech();
  const seq = audioState.requestSeq;
  const controller = new AbortController();
  audioState.abortController = controller;
  setAudioActiveState(true);
  setActiveAudioButtonState(triggerBtn, "loading");

  try {
    const res = await fetch("/api/tts", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        text: clean,
        lang,
        offline: state.lowDataMode
      }),
      signal: controller.signal
    });

    if (audioState.requestSeq !== seq) return;
    if (!res.ok) {
      const errBody = await res.json().catch(() => null);
      throw new Error(errBody?.message || `HTTP ${res.status}`);
    }

    const blob = await res.blob();
    if (audioState.requestSeq !== seq) return;
    if (!blob || blob.size < 44) {
      throw new Error("Áudio vazio");
    }

    const objectUrl = URL.createObjectURL(blob);
    const audio = new Audio(objectUrl);
    audio.playbackRate = effectiveRate;
    audioState.objectUrl = objectUrl;
    audioState.element = audio;

    audio.onplaying = () => {
      if (audioState.requestSeq === seq) {
        setActiveAudioButtonState(triggerBtn, "playing");
      }
    };
    audio.onended = () => {
      if (audioState.requestSeq === seq) {
        releaseCurrentAudio();
        setAudioActiveState(false);
      }
    };
    audio.onerror = () => {
      if (audioState.requestSeq === seq) {
        releaseCurrentAudio();
        if (!speakViaBrowserFallback(clean, lang, effectiveRate, seq, triggerBtn)) {
          setAudioActiveState(false);
          showToast("Não foi possível reproduzir o áudio.");
        }
      }
    };

    await audio.play();
    if (audioState.requestSeq === seq) {
      setActiveAudioButtonState(triggerBtn, "playing");
    }
  } catch (err) {
    if (err && err.name === "AbortError") {
      return;
    }
    if (audioState.requestSeq !== seq) return;
    releaseCurrentAudio();
    if (!speakViaBrowserFallback(clean, lang, effectiveRate, seq, triggerBtn)) {
      setAudioActiveState(false);
      showToast("Não foi possível reproduzir o áudio agora.");
    }
  }
}

function speakPortuguese(text, triggerBtn = null) {
  playTtsFromBackend(text, "pt-BR", 1.0, triggerBtn);
}

function speakEnglishExamples(phrases, triggerBtn = null) {
  const valid = (phrases || []).map((p) => String(p || "").trim()).filter(Boolean);
  if (!valid.length) return;
  const joined = valid.join(". ");
  playTtsFromBackend(joined, "en-US", 0.95, triggerBtn);
}

function openFeedbackModal(watermarkId) {
  el.feedbackWatermark.textContent = watermarkId;
  el.feedbackComment.value = "";
  el.feedbackDialog.showModal();
}

function createStreamingAiBubble() {
  const node = document.createElement("article");
  node.className = "msg msg-ai msg-streaming";
  node.innerHTML = `
    <div class="msg-header">
      <span class="skill-badge">Dinga AI · Respondendo...</span>
    </div>
    <div class="msg-body"></div>
  `;
  el.chatLog.appendChild(node);
  el.chatLog.scrollTop = el.chatLog.scrollHeight;
  return node;
}

async function consumeChatStream(payload, onFirstToken, onToken) {
  const res = await fetch("/api/chat/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream"
    },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    const errBody = await res.json().catch(() => null);
    const err = new Error(errBody?.message || `HTTP ${res.status}`);
    err.status = res.status;
    throw err;
  }

  const contentType = (res.headers.get("content-type") || "").toLowerCase();
  if (!contentType.includes("text/event-stream") || !res.body) {
    return null;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let currentEvent = "";
  let dataLines = [];
  let finalData = null;
  let started = false;

  const dispatchEvent = () => {
    if (!dataLines.length) {
      currentEvent = "";
      return;
    }
    const rawData = dataLines.join("\n");
    const eventName = currentEvent || "message";
    currentEvent = "";
    dataLines = [];

    try {
      const parsed = JSON.parse(rawData);
      if (eventName === "token" && typeof parsed.delta === "string") {
        if (!started) {
          started = true;
          onFirstToken();
        }
        onToken(parsed.delta);
      } else if (eventName === "done") {
        finalData = parsed;
      }
    } catch {
      // ignore malformed SSE chunk
    }
  };

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      buffer += decoder.decode();
      break;
    }
    buffer += decoder.decode(value, { stream: true });

    let nlIdx;
    while ((nlIdx = buffer.indexOf("\n")) !== -1) {
      const line = buffer.slice(0, nlIdx).replace(/\r$/, "");
      buffer = buffer.slice(nlIdx + 1);

      if (line === "") {
        dispatchEvent();
      } else if (line.startsWith("event:")) {
        currentEvent = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).trimStart());
      }
    }
  }
  const trailing = buffer.replace(/\r$/, "");
  if (trailing.startsWith("event:")) {
    currentEvent = trailing.slice(6).trim();
  } else if (trailing.startsWith("data:")) {
    dataLines.push(trailing.slice(5).trimStart());
  }
  if (dataLines.length) {
    dispatchEvent();
  }
  return finalData;
}

async function submitChat(messageText) {
  const message = messageText.trim();
  if (!message) {
    return;
  }

  appendUserMessage(message);
  el.chatInput.value = "";
  hideChallengeReplyBanner();
  el.sendBtn.disabled = true;
  el.sendBtn.textContent = "...";
  const loadingNode = appendLoadingIndicator();
  let streamingNode = null;
  let streamedText = "";

  const recentHistory = state.history.slice(-4).map((item) => ({
    question: item.question,
    reply: item.response?.reply || "",
    practicePrompt: item.response?.practicePrompt || "",
    skillId: item.response?.activeSkill?.id || ""
  }));

  const payload = {
    message,
    level: el.level.value,
    goal: el.goal.value,
    preferredSkill: el.skillSelect.value,
    lowDataMode: state.lowDataMode,
    history: recentHistory,
    learnerState: state.learnerState
  };

  const t0 = performance.now();
  try {
    let data = null;
    try {
      data = await consumeChatStream(
        payload,
        () => {
          loadingNode.remove();
          streamingNode = createStreamingAiBubble();
        },
        (delta) => {
          streamedText += delta;
          if (streamingNode) {
            const bodyEl = streamingNode.querySelector(".msg-body");
            if (bodyEl) {
              bodyEl.innerHTML = renderMiniMarkdown(streamedText);
            }
            el.chatLog.scrollTop = el.chatLog.scrollHeight;
          }
        }
      );
    } catch (streamErr) {
      if (streamingNode) {
        streamingNode.remove();
        streamingNode = null;
      }
      // Se erro HTTP 4xx vindo da API (ex.: 400 validação ou 429 rate-limit), propaga diretamente sem duplicar chamada
      if (streamErr && typeof streamErr.status === "number" && streamErr.status >= 400 && streamErr.status < 500) {
        throw streamErr;
      }
    }

    if (!data) {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      if (!res.ok) {
        const errBody = await res.json().catch(() => null);
        throw new Error(errBody?.message || `HTTP ${res.status}`);
      }
      data = await res.json();
    }

    const rttMs = Math.max(1, Math.round(performance.now() - t0));

    loadingNode.remove();
    if (streamingNode) {
      streamingNode.remove();
    }
    state.history.push({ question: message, response: data });
    state.lastLatencyMs = data.mtg?.responseTimeMs || rttMs;
    state.totalSessionKb += Number(data.mtg?.payloadSizeKb || 1.2);
    updateMemoryAndTelemetryBar();

    appendAiResponse(data, rttMs);

    if (data.learnerStateDelta) {
      applyLearnerStateDelta(data.learnerStateDelta);
    }

    const challengeOutcome = detectMicroChallengeOutcome(data.reply);
    if (challengeOutcome !== null) {
      recordAdaptiveOutcome(challengeOutcome);
    }
  } catch (err) {
    loadingNode.remove();
    if (streamingNode) {
      streamingNode.remove();
    }
    const errNode = document.createElement("div");
    errNode.className = "msg";
    errNode.innerHTML = `<p style="color:var(--danger)">Erro ao obter resposta (${escapeHtml(err.message)}).</p>`;
    el.chatLog.appendChild(errNode);
  } finally {
    el.sendBtn.disabled = false;
    el.sendBtn.textContent = "Enviar";
  }
}

function renderSkillsAndDemoPrompts(skills) {
  el.demoPrompts.innerHTML = "";
  el.skillsGrid.innerHTML = "";

  skills.forEach((skill) => {
    const opt = document.createElement("option");
    opt.value = skill.id;
    opt.textContent = skill.name;
    el.skillSelect.appendChild(opt);

    const demoBtn = document.createElement("button");
    demoBtn.type = "button";
    demoBtn.className = "demo-btn";
    demoBtn.innerHTML = `<strong>${escapeHtml(skill.name)}</strong>${escapeHtml(skill.samplePrompt)}`;
    demoBtn.addEventListener("click", () => {
      el.skillSelect.value = "auto";
      submitChat(skill.samplePrompt);
    });
    el.demoPrompts.appendChild(demoBtn);

    const card = document.createElement("article");
    card.className = "card-block";
    card.innerHTML = `
      <span class="skill-badge">${escapeHtml(skill.badge)}</span>
      <h3 style="margin:8px 0 4px">${escapeHtml(skill.name)}</h3>
      <p class="small muted" style="margin:0 0 10px">${escapeHtml(skill.description)}</p>
      <button class="btn-chip btn-xs test-skill-btn" type="button">Testar no Chat</button>
    `;
    card.querySelector(".test-skill-btn").addEventListener("click", () => {
      el.skillSelect.value = skill.id;
      activateTab("tutor");
      submitChat(skill.samplePrompt);
    });
    el.skillsGrid.appendChild(card);
  });
}

function populateQuizDocSelect(docs) {
  if (!el.quizGenDoc) return;
  const currentVal = el.quizGenDoc.value;
  el.quizGenDoc.innerHTML = `<option value="ALL">Automático (Base Curada)</option>`;
  (docs || []).forEach((doc) => {
    const opt = document.createElement("option");
    opt.value = doc.docId;
    opt.textContent = `[${doc.level}] ${doc.title}`;
    el.quizGenDoc.appendChild(opt);
  });
  if (currentVal && Array.from(el.quizGenDoc.options).some((o) => o.value === currentVal)) {
    el.quizGenDoc.value = currentVal;
  }
}

function renderKnowledge(docs, filter = "") {
  if (el.docsCountLabel) {
    el.docsCountLabel.textContent = String(docs.length);
  }
  populateQuizDocSelect(docs);

  const q = filter.trim().toLowerCase();
  const filtered = docs.filter(
    (d) =>
      !q ||
      d.title.toLowerCase().includes(q) ||
      d.category.toLowerCase().includes(q) ||
      d.level.toLowerCase().includes(q) ||
      d.sourceRef.toLowerCase().includes(q)
  );

  el.knowledgeList.innerHTML = "";
  filtered.forEach((doc) => {
    const docChunks = (state.knowledge.chunks || []).filter((c) => c.docId === doc.docId);
    const chunksPreviewHtml = docChunks
      .map(
        (c) => `
        <div class="rag-chunk">
          <strong>${escapeHtml(c.section)}</strong>
          <div>${renderMiniMarkdown(c.content)}</div>
        </div>`
      )
      .join("");

    const isCustomDoc = doc.docId.includes("-docente-");
    const card = document.createElement("article");
    card.className = "card-block knowledge-card";
    card.innerHTML = `
      <div>
        <div class="brand-title-row">
          <span class="pill pill-accent">${escapeHtml(doc.level)}</span>
          <span class="pill">${escapeHtml(doc.category)}</span>
        </div>
        <h3 style="margin:8px 0 4px">${escapeHtml(doc.title)}</h3>
        <details class="rag-details">
          <summary>Ver conteúdo (${doc.chunkCount})</summary>
          ${chunksPreviewHtml}
        </details>
      </div>
      <div class="knowledge-card-actions">
        <button class="btn-chip btn-xs ask-doc-btn" type="button">Estudar no Chat</button>
        <button class="btn-chip btn-xs gen-quiz-doc-btn" type="button">+ Questão Quiz</button>
        ${isCustomDoc ? `<button class="btn-ghost btn-xs btn-danger-outline delete-doc-btn" type="button">Remover</button>` : ""}
      </div>
    `;

    card.querySelector(".ask-doc-btn").addEventListener("click", () => {
      el.skillSelect.value = "auto";
      activateTab("tutor");
      submitChat(`Me explique os principais conceitos e exemplos em inglês sobre: ${doc.title}`);
    });

    card.querySelector(".gen-quiz-doc-btn").addEventListener("click", async () => {
      await triggerQuickQuizGeneration({
        docId: doc.docId,
        level: doc.level === "ALL" ? el.level.value : doc.level,
        topic: "",
        count: 1
      });
    });

    card.querySelector(".delete-doc-btn")?.addEventListener("click", async () => {
      try {
        const res = await fetch(`/api/knowledge/${encodeURIComponent(doc.docId)}`, { method: "DELETE" });
        if (!res.ok) {
      const errBody = await res.json().catch(() => null);
      throw new Error(errBody?.message || `HTTP ${res.status}`);
    }
        const knowledgeRes = await fetch("/api/knowledge");
        state.knowledge = await knowledgeRes.json();
        renderKnowledge(state.knowledge.documents, el.knowledgeFilter.value);
        showToast("Documento removido.");
      } catch (err) {
        showToast(`Erro ao remover: ${err.message}`);
      }
    });

    el.knowledgeList.appendChild(card);
  });
}

function setupTeacherRagPanel() {
  if (!el.teacherRagForm) return;

  async function uploadKnowledgeFile(file) {
    if (!file) return;
    const lower = (file.name || "").toLowerCase();
    if (!lower.endsWith(".md") && !lower.endsWith(".txt")) {
      showToast("Formato inválido. Envie um arquivo .md ou .txt.");
      return;
    }
    const formData = new FormData();
    formData.append("file", file);

    try {
      showToast(`Indexando ${file.name}...`);
      const res = await fetch("/api/knowledge/upload", {
        method: "POST",
        body: formData
      });
      if (!res.ok) {
        const errBody = await res.json().catch(() => null);
        throw new Error(errBody?.message || `HTTP ${res.status}`);
      }
      const created = await res.json();
      const knowledgeRes = await fetch("/api/knowledge");
      state.knowledge = await knowledgeRes.json();
      renderKnowledge(state.knowledge.documents, el.knowledgeFilter.value);

      const testPrompt =
        created.suggestedPrompt || `Me explique os principais conceitos de ${created.document.title}`;

      el.teacherRagStatus.hidden = false;
      el.teacherRagStatus.innerHTML = `
        <span>Arquivo <code>${escapeHtml(file.name)}</code> indexado como <code>${escapeHtml(created.document.docId)}</code> (${created.document.chunkCount} seções · ${created.totalDocuments} docs).</span>
        <div class="practice-box-actions">
          <button id="test-new-rag-btn" class="btn-chip btn-xs" type="button">Testar no Chat</button>
          <button id="gen-quiz-new-rag-btn" class="btn-primary btn-xs" type="button">+ Questão Quiz</button>
        </div>
      `;

      document.getElementById("test-new-rag-btn").addEventListener("click", () => {
        el.skillSelect.value = "auto";
        activateTab("tutor");
        submitChat(testPrompt);
      });

      document.getElementById("gen-quiz-new-rag-btn").addEventListener("click", async () => {
        await triggerQuickQuizGeneration({
          docId: created.document.docId,
          level: created.document.level === "ALL" ? el.level.value : created.document.level,
          topic: "",
          count: 1
        });
      });

      showToast(`Arquivo ${file.name} indexado no BM25.`);
    } catch (err) {
      showToast(`Erro no upload: ${err.message}`);
    } finally {
      if (el.ragFileInput) el.ragFileInput.value = "";
    }
  }

  if (el.ragUploadDropzone && el.ragFileInput) {
    el.ragUploadDropzone.addEventListener("click", () => el.ragFileInput.click());
    el.ragUploadDropzone.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        el.ragFileInput.click();
      }
    });
    el.ragFileInput.addEventListener("change", () => {
      const file = el.ragFileInput.files?.[0];
      if (file) uploadKnowledgeFile(file);
    });
    el.ragUploadDropzone.addEventListener("dragover", (e) => {
      e.preventDefault();
      el.ragUploadDropzone.classList.add("dragover");
    });
    el.ragUploadDropzone.addEventListener("dragleave", () => {
      el.ragUploadDropzone.classList.remove("dragover");
    });
    el.ragUploadDropzone.addEventListener("drop", (e) => {
      e.preventDefault();
      el.ragUploadDropzone.classList.remove("dragover");
      const file = e.dataTransfer?.files?.[0];
      if (file) uploadKnowledgeFile(file);
    });
  }

  el.fillSampleRag?.addEventListener("click", () => {
    el.ragDocTitle.value = "Azure AI Studio — Prompt Flow, Guardrails e TPM";
    el.ragDocLevel.value = "B1";
    el.ragDocCategory.value = "Curadoria Docente (Microsoft Learn)";
    el.ragDocSection.value = "Vocabulário de Prompt Flow, Guardrails e Rate Limit (TPM)";
    el.ragDocSource.value = "Painel Docente USJT";
    el.ragDocContent.value = [
      "No **Azure AI Studio** e nas trilhas do **Microsoft Learn**, três termos em inglês são essenciais:",
      "- **Prompt Flow:** fluxo que encadeia chamadas de LLM e RAG (*\"We built a Prompt Flow to orchestrate RAG and safety checks.\"*).",
      "- **Guardrails (Content Safety):** barreiras que bloqueiam conteúdo tóxico ou viés (*\"Responsible AI guardrails filter harmful outputs before they reach the student.\"*).",
      "- **TPM (Tokens Per Minute):** limite de tokens por minuto da API (*\"When the API hits the TPM rate limit, Dinga AI switches to local RAG fallback.\"*)."
    ].join("\n");
  });

  el.fillSampleRagGit?.addEventListener("click", () => {
    el.ragDocTitle.value = "Git Avançado — Rebase, Cherry-pick e Merge Conflict";
    el.ragDocLevel.value = "B1";
    el.ragDocCategory.value = "Curadoria Docente (Cloud & Git)";
    el.ragDocSection.value = "Vocabulário de Versionamento e Code Review";
    el.ragDocSource.value = "Painel Docente USJT";
    el.ragDocContent.value = [
      "Para colaborar em repositórios em inglês:",
      "- **Merge Conflict:** conflito quando duas branches editam a mesma linha (*\"I resolved the merge conflict before updating the Pull Request.\"*).",
      "- **Rebase:** reaplicar commits sobre a branch principal (*\"Please rebase your feature branch onto main.\"*).",
      "- **Cherry-pick:** aplicar um commit específico de outra branch (*\"We cherry-picked the hotfix commit into production.\"*)."
    ].join("\n");
  });

  el.fillSampleRagDaily?.addEventListener("click", () => {
    el.ragDocTitle.value = "Phrasal Verbs Essenciais para Daily Standup";
    el.ragDocLevel.value = "B2";
    el.ragDocCategory.value = "Curadoria Docente (Carreira)";
    el.ragDocSection.value = "Expressões Naturais para Reuniões Ágeis";
    el.ragDocSource.value = "Painel Docente USJT";
    el.ragDocContent.value = [
      "Phrasal verbs usados por times globais:",
      "- **Follow up:** acompanhar uma pendência (*\"I will follow up with the QA team after the daily standup.\"*).",
      "- **Look into:** investigar um problema técnico (*\"I am looking into the latency issue on the login endpoint.\"*).",
      "- **Roll out:** disponibilizar gradualmente (*\"We plan to roll out the new AI tutor feature tomorrow.\"*)."
    ].join("\n");
  });

  el.teacherRagForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    el.submitRagBtn.disabled = true;
    el.submitRagBtn.textContent = "Indexando...";

    try {
      const payload = {
        title: el.ragDocTitle.value,
        level: el.ragDocLevel.value,
        category: el.ragDocCategory.value,
        section: el.ragDocSection.value,
        sourceRef: el.ragDocSource.value,
        content: el.ragDocContent.value
      };

      const res = await fetch("/api/knowledge", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      if (!res.ok) {
        const errBody = await res.json().catch(() => null);
        throw new Error(errBody?.message || `HTTP ${res.status}`);
      }
      const created = await res.json();

      const knowledgeRes = await fetch("/api/knowledge");
      state.knowledge = await knowledgeRes.json();
      renderKnowledge(state.knowledge.documents, el.knowledgeFilter.value);

      const testPrompt =
        created.suggestedPrompt || `O que significam ${payload.title} e ${payload.section}?`;

      el.teacherRagStatus.hidden = false;
      el.teacherRagStatus.innerHTML = `
        <span>Documento <code>${escapeHtml(created.document.docId)}</code> indexado (${created.totalDocuments} docs).</span>
        <div class="practice-box-actions">
          <button id="test-new-rag-btn" class="btn-chip btn-xs" type="button">Testar no Chat</button>
          <button id="gen-quiz-new-rag-btn" class="btn-primary btn-xs" type="button">+ Questão Quiz</button>
        </div>
      `;

      document.getElementById("test-new-rag-btn").addEventListener("click", () => {
        el.skillSelect.value = "auto";
        activateTab("tutor");
        submitChat(testPrompt);
      });

      document.getElementById("gen-quiz-new-rag-btn").addEventListener("click", async () => {
        await triggerQuickQuizGeneration({
          docId: created.document.docId,
          level: payload.level === "ALL" ? el.level.value : payload.level,
          topic: "",
          count: 1
        });
      });

      showToast("Documento indexado no RAG.");
    } catch (err) {
      showToast(`Erro ao indexar: ${err.message}`);
    } finally {
      el.submitRagBtn.disabled = false;
      el.submitRagBtn.textContent = "Indexar documento";
    }
  });
}

async function loadQuizQuestions() {
  const res = await fetch("/api/quiz");
  state.quiz.questions = await res.json();
  reconcileSavedQuizAnswers();
  renderQuiz();
}

function reconcileSavedQuizAnswers() {
  const validIds = new Set(state.quiz.questions.map((q) => q.id));
  const reconciled = {};
  let answeredCount = 0;
  let correctCount = 0;
  let pruned = false;

  Object.entries(state.quiz.answered || {}).forEach(([qId, ans]) => {
    if (validIds.has(qId) && ans && typeof ans === "object") {
      reconciled[qId] = ans;
      answeredCount += 1;
      if (ans.correct) correctCount += 1;
    } else {
      pruned = true;
    }
  });

  state.quiz.answered = reconciled;
  state.quiz.totalAnswered = answeredCount;
  state.quiz.correctCount = correctCount;
  if (answeredCount === 0) {
    state.quiz.streak = 0;
  }
  if (pruned) {
    saveLocalProgress();
  }
}

async function triggerQuickQuizGeneration({ docId = "", level = "B1", topic = "", count = 1 }) {
  activateTab("quiz");
  if (el.quizGenDoc && docId) {
    el.quizGenDoc.value = docId;
  }
  if (el.quizGenLevel && level) {
    el.quizGenLevel.value = level;
  }
  if (el.quizGenTopic) {
    el.quizGenTopic.value = topic;
  }
  if (el.quizGenCount) {
    el.quizGenCount.value = String(count);
  }
  await executeQuizGeneration({ docId, level, topic, count });
}

async function executeQuizGeneration({ docId, level, topic, count }) {
  if (el.quizGenBtn) {
    el.quizGenBtn.disabled = true;
    el.quizGenBtn.textContent = "Gerando...";
  }
  try {
    const res = await fetch("/api/quiz/generate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ docId, level, topic, count: Number(count) || 1 })
    });
    if (!res.ok) {
      const errBody = await res.json().catch(() => null);
      throw new Error(errBody?.message || `HTTP ${res.status}`);
    }
    const batch = await res.json();
    const createdList = batch.generated || [];

    if (el.quizCategoryFilter) el.quizCategoryFilter.value = "ALL";
    if (el.quizLevelFilter) el.quizLevelFilter.value = "ALL";

    await loadQuizQuestions();

    if (createdList.length > 0) {
      const firstNewId = createdList[0].id;
      const newIdx = state.quiz.questions.findIndex((q) => q.id === firstNewId);
      if (newIdx >= 0) {
        state.quiz.focusIndex = newIdx;
      }
      renderQuiz(firstNewId);

      if (el.quizGenStatus) {
        el.quizGenStatus.hidden = false;
        const modeLabel = batch.generationMode ? ` · ${escapeHtml(batch.generationMode)}` : "";
        el.quizGenStatus.innerHTML = `
          <span>+${createdList.length} questão gerada (${batch.totalQuestions} no total${modeLabel}).</span>
        `;
      }
      showToast(`+${createdList.length} questão adicionada ao Quiz.`);
    }
  } catch (err) {
    showToast(`Falha ao gerar questão: ${err.message}`);
  } finally {
    if (el.quizGenBtn) {
      el.quizGenBtn.disabled = false;
      el.quizGenBtn.textContent = "Gerar";
    }
  }
}

function getFilteredQuizQuestions() {
  const catFilter = (el.quizCategoryFilter?.value || "ALL").toLowerCase();
  const lvlFilter = (el.quizLevelFilter?.value || "ALL").toUpperCase();

  return state.quiz.questions.filter((q) => {
    const catLower = (q.category || "").toLowerCase();
    const matchCat =
      catFilter === "all" ||
      catFilter
        .split("|")
        .map((token) => token.trim())
        .some((token) => token && catLower.includes(token));
    const matchLvl = lvlFilter === "ALL" || q.level.toUpperCase() === lvlFilter;
    return matchCat && matchLvl;
  });
}

function updateQuizScoreboard(filteredQuestions) {
  const totalQ = state.quiz.questions.length;
  if (el.tabQuizCount) {
    el.tabQuizCount.textContent = String(totalQ);
  }
  if (el.quizScorePill) {
    el.quizScorePill.textContent = `Acertos: ${state.quiz.correctCount}/${state.quiz.totalAnswered}`;
  }
  if (el.quizStreakPill) {
    el.quizStreakPill.textContent = `Sequência: ${state.quiz.streak}`;
  }

  const pctDone = totalQ > 0 ? Math.min(100, Math.round((state.quiz.totalAnswered / totalQ) * 100)) : 0;
  const accuracyPct =
    state.quiz.totalAnswered > 0
      ? Math.round((state.quiz.correctCount / state.quiz.totalAnswered) * 100)
      : null;

  if (el.quizProgressText) {
    el.quizProgressText.textContent = `${state.quiz.totalAnswered} de ${totalQ} respondidas`;
  }
  if (el.quizAccuracyText) {
    el.quizAccuracyText.textContent =
      accuracyPct !== null ? `Aproveitamento: ${accuracyPct}%` : "Aproveitamento: --%";
  }
  if (el.quizProgressFill) {
    el.quizProgressFill.style.width = `${pctDone}%`;
    el.quizProgressFill.parentElement?.setAttribute("aria-valuenow", String(pctDone));
  }

  if (el.quizStepper && filteredQuestions) {
    el.quizStepper.innerHTML = "";
    filteredQuestions.forEach((q, idx) => {
      const ans = state.quiz.answered[q.id];
      const dot = document.createElement("button");
      dot.type = "button";
      let cls = "stepper-dot";
      if (idx === state.quiz.focusIndex) cls += " active";
      if (ans) cls += ans.correct ? " done-correct" : " done-wrong";
      dot.className = cls;
      dot.title = `${q.question} (${q.level})`;
      dot.textContent = String(idx + 1);
      dot.addEventListener("click", () => {
        state.quiz.focusIndex = idx;
        renderQuiz();
        if (state.quiz.viewMode === "list") {
          document.getElementById(`quiz-card-${q.id}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
        }
      });
      el.quizStepper.appendChild(dot);
    });
  }
}

function setQuizViewMode(mode) {
  state.quiz.viewMode = mode;
  if (el.quizViewList && el.quizViewFocus) {
    const isList = mode === "list";
    el.quizViewList.classList.toggle("active", isList);
    el.quizViewList.setAttribute("aria-pressed", String(isList));
    el.quizViewFocus.classList.toggle("active", !isList);
    el.quizViewFocus.setAttribute("aria-pressed", String(!isList));
  }
  saveLocalProgress();
  renderQuiz();
}

function renderQuiz(highlightId = "") {
  if (!el.quizContainer) return;

  const filtered = getFilteredQuizQuestions();
  if (state.quiz.focusIndex >= filtered.length) {
    state.quiz.focusIndex = Math.max(0, filtered.length - 1);
  }
  updateQuizScoreboard(filtered);

  if (!filtered.length) {
    el.quizContainer.innerHTML = `<p class="muted small">Nenhuma questão para este filtro.</p>`;
    return;
  }

  const questionsToRender =
    state.quiz.viewMode === "focus"
      ? [{ q: filtered[state.quiz.focusIndex], idx: state.quiz.focusIndex }]
      : filtered.map((q, idx) => ({ q, idx }));

  el.quizContainer.innerHTML = "";

  questionsToRender.forEach(({ q, idx }) => {
    const answeredState = state.quiz.answered[q.id];
    const isGenerated = q.id.startsWith("quiz-gen-");
    const card = document.createElement("article");
    card.id = `quiz-card-${q.id}`;
    card.className = `quiz-card${isGenerated || q.id === highlightId ? " generated-highlight" : ""}`;

    const optionsHtml = q.options
      .map((optText, optIdx) => {
        let extraClass = "";
        if (answeredState) {
          if (optIdx === q.correctIndex) extraClass = "correct";
          else if (optIdx === answeredState.selectedIndex) extraClass = "incorrect";
        }
        const letter = String.fromCharCode(65 + optIdx);
        return `
          <button
            class="quiz-option-btn ${extraClass}"
            type="button"
            data-opt-idx="${optIdx}"
            ${answeredState ? "disabled" : ""}
          >
            <strong>${letter})</strong> ${escapeHtml(optText)}
          </button>
        `;
      })
      .join("");

    const feedbackHtml = answeredState
      ? `
        <div class="quiz-feedback-box">
          <strong>${answeredState.correct ? "Correto!" : "Incorreto."}</strong>
          <p style="margin:4px 0">${escapeHtml(answeredState.explanation)}</p>
          <p class="small muted" style="margin:4px 0">
            Exemplo: <em>"${escapeHtml(q.englishExample)}"</em>
          </p>
          <div class="mtg-actions" style="margin-top:8px">
            <button class="btn-chip btn-xs quiz-speak-example-btn" type="button">🔊 Ouvir exemplo</button>
            <button class="btn-chip btn-xs quiz-save-vocab-btn" type="button">+ Salvar no Caderno</button>
            <button class="btn-chip btn-xs quiz-ask-tutor-btn" type="button">Perguntar ao Tutor</button>
          </div>
        </div>
      `
      : "";

    const focusNavHtml =
      state.quiz.viewMode === "focus"
        ? `
        <div class="quiz-focus-nav">
          <button class="btn-chip btn-xs quiz-prev-btn" type="button" ${idx === 0 ? "disabled" : ""}>&larr; Anterior</button>
          <span class="small muted">${idx + 1} / ${filtered.length}</span>
          <button class="btn-primary btn-xs quiz-next-btn" type="button" ${idx >= filtered.length - 1 ? "disabled" : ""}>Próxima &rarr;</button>
        </div>
      `
        : "";

    card.innerHTML = `
      <div class="brand-title-row" style="justify-content:space-between">
        <div>
          <span class="pill pill-accent">${escapeHtml(q.level)}</span>
          <span class="pill">${escapeHtml(q.category)}</span>
          ${isGenerated ? `<span class="pill">Gerada via RAG</span>` : ""}
        </div>
        <div class="brand-title-row">
          ${isGenerated ? `<button class="btn-ghost btn-xs btn-danger-outline delete-quiz-btn" type="button">Remover</button>` : ""}
          <span class="small muted">Questão ${idx + 1}</span>
        </div>
      </div>
      <h3 style="margin:10px 0 6px;font-size:0.96rem">${escapeHtml(q.question)}</h3>
      <div class="quiz-snippet">
        <span>${escapeHtml(q.codeOrDocSnippet)}</span>
        <button class="btn-chip btn-xs quiz-speak-snippet-btn" type="button" title="Ouvir em Inglês">🔊 EN</button>
      </div>
      <div class="quiz-options">${optionsHtml}</div>
      ${feedbackHtml}
      ${focusNavHtml}
    `;

    card.querySelector(".quiz-speak-snippet-btn").addEventListener("click", (e) => {
      speakEnglishExamples([q.codeOrDocSnippet.replace(/^"|"$/g, "")], e.currentTarget);
    });

    card.querySelector(".delete-quiz-btn")?.addEventListener("click", async () => {
      try {
        const res = await fetch(`/api/quiz/${encodeURIComponent(q.id)}`, { method: "DELETE" });
        if (!res.ok) {
      const errBody = await res.json().catch(() => null);
      throw new Error(errBody?.message || `HTTP ${res.status}`);
    }
        const prevAns = state.quiz.answered[q.id];
        if (prevAns) {
          state.quiz.totalAnswered = Math.max(0, state.quiz.totalAnswered - 1);
          if (prevAns.correct) {
            state.quiz.correctCount = Math.max(0, state.quiz.correctCount - 1);
          }
          delete state.quiz.answered[q.id];
          saveLocalProgress();
        }
        await loadQuizQuestions();
        showToast("Questão removida.");
      } catch (err) {
        showToast(`Erro ao remover: ${err.message}`);
      }
    });

    card.querySelectorAll(".quiz-option-btn").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const selectedIndex = Number(btn.dataset.optIdx);
        await handleQuizAnswer(q, selectedIndex);
      });
    });

    if (answeredState) {
      card.querySelector(".quiz-speak-example-btn")?.addEventListener("click", (e) => {
        speakEnglishExamples([q.englishExample], e.currentTarget);
      });
      card.querySelector(".quiz-save-vocab-btn")?.addEventListener("click", (e) => {
        const added = addVocabItem(q.englishExample, answeredState.explanation, `Quiz · ${q.category} (${q.level})`);
        e.currentTarget.textContent = "✓ Salvo";
        showToast(added ? "Exemplo salvo no Caderno de Vocabulário." : "Exemplo já estava no Caderno.");
      });
      card.querySelector(".quiz-ask-tutor-btn")?.addEventListener("click", () => {
        el.skillSelect.value = "auto";
        activateTab("tutor");
        submitChat(answeredState.followUpPrompt || q.question);
      });
    }

    if (state.quiz.viewMode === "focus") {
      card.querySelector(".quiz-prev-btn")?.addEventListener("click", () => {
        if (state.quiz.focusIndex > 0) {
          state.quiz.focusIndex -= 1;
          renderQuiz();
        }
      });
      card.querySelector(".quiz-next-btn")?.addEventListener("click", () => {
        if (state.quiz.focusIndex < filtered.length - 1) {
          state.quiz.focusIndex += 1;
          renderQuiz();
        }
      });
    }

    el.quizContainer.appendChild(card);
  });

  if (highlightId && state.quiz.viewMode === "list") {
    document.getElementById(`quiz-card-${highlightId}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
  }
}

async function handleQuizAnswer(question, selectedIndex) {
  if (state.quiz.answered[question.id]) return;

  let result;
  try {
    const res = await fetch("/api/quiz/answer", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ questionId: question.id, selectedIndex })
    });
    if (res.ok) {
      result = await res.json();
    }
  } catch (_) {
    // Fallback local
  }

  if (!result) {
    const correct = selectedIndex === question.correctIndex;
    result = {
      questionId: question.id,
      correct,
      selectedIndex,
      correctIndex: question.correctIndex,
      explanation: question.explanation,
      englishExample: question.englishExample,
      sourceDocId: question.sourceDocId,
      followUpPrompt: `Me explique mais exemplos práticos sobre: ${question.question}`
    };
  }

  state.quiz.answered[question.id] = result;
  state.quiz.totalAnswered += 1;
  if (result.correct) {
    state.quiz.correctCount += 1;
    state.quiz.streak += 1;
  } else {
    state.quiz.streak = 0;
  }
  recordAdaptiveOutcome(result.correct);
  recordLearnerQuizOutcome(question.category, result.correct);
  saveLocalProgress();
  renderQuiz();
}

function setupQuizControls() {
  el.quizResetBtn?.addEventListener("click", () => {
    state.quiz.answered = {};
    state.quiz.correctCount = 0;
    state.quiz.totalAnswered = 0;
    state.quiz.streak = 0;
    state.quiz.focusIndex = 0;
    saveLocalProgress();
    renderQuiz();
  });

  el.quizViewList?.addEventListener("click", () => setQuizViewMode("list"));
  el.quizViewFocus?.addEventListener("click", () => setQuizViewMode("focus"));

  el.quizCategoryFilter?.addEventListener("change", () => {
    state.quiz.focusIndex = 0;
    renderQuiz();
  });
  el.quizLevelFilter?.addEventListener("change", () => {
    state.quiz.focusIndex = 0;
    renderQuiz();
  });

  el.quizGeneratorForm?.addEventListener("submit", async (e) => {
    e.preventDefault();
    await executeQuizGeneration({
      topic: el.quizGenTopic?.value || "",
      docId: el.quizGenDoc?.value || "ALL",
      level: el.quizGenLevel?.value || "B1",
      count: Number(el.quizGenCount?.value || 1)
    });
  });
}

async function refreshFeedbackList() {
  const res = await fetch("/api/feedback");
  const reports = await res.json();
  el.feedbackList.innerHTML = reports.length
    ? reports
        .map(
          (r) => `
          <div class="card-block">
            <div class="brand-title-row" style="justify-content:space-between">
              <div>
                <strong>${escapeHtml(r.category)}</strong> · <code>${escapeHtml(r.watermarkId || "sem-id")}</code>
              </div>
              <button class="btn-ghost btn-xs btn-danger-outline delete-feedback-btn" type="button" data-report-id="${escapeHtml(r.id)}">Remover</button>
            </div>
            <p class="small" style="margin:4px 0">${escapeHtml(r.comment)}</p>
            <span class="small muted">${escapeHtml(r.id)} · ${escapeHtml(r.createdAt)}</span>
          </div>`
        )
        .join("")
    : `<p class="muted small">Nenhum relato registrado.</p>`;

  el.feedbackList.querySelectorAll(".delete-feedback-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      try {
        const delRes = await fetch(`/api/feedback/${encodeURIComponent(btn.dataset.reportId)}`, {
          method: "DELETE"
        });
        if (!delRes.ok) throw new Error(`HTTP ${delRes.status}`);
        await refreshFeedbackList();
        showToast("Relato removido.");
      } catch (err) {
        showToast(`Erro ao remover: ${err.message}`);
      }
    });
  });
}

function csvEscape(value) {
  const str = String(value ?? "").replace(/\r?\n/g, " ").trim();
  return `"${str.replace(/"/g, '""')}"`;
}

function exportVocabToAnkiCsv() {
  if (!state.vocab.items.length) {
    showToast("Salve ao menos um termo no Caderno antes de exportar.");
    return;
  }
  const rows = ["Front,Back,Tags"];
  state.vocab.items.forEach((item) => {
    const front = csvEscape(item.english);
    const backText = item.context ? `${item.meaning} [${item.context}]` : item.meaning;
    const back = csvEscape(backText);
    const tags = csvEscape(`dinga-ai box-${item.box || 1}`);
    rows.push(`${front},${back},${tags}`);
  });

  // BOM UTF-8 para abrir corretamente no Anki e Excel/LibreOffice
  const blob = new Blob(["\uFEFF" + rows.join("\r\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "vocabulario-anki-dinga-ai.csv";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  showToast(`Vocabulário exportado (${state.vocab.items.length} termos · formato Anki CSV).`);
}

function buildSessionReportLines() {
  const quizAcc =
    state.quiz.totalAnswered > 0
      ? `${Math.round((state.quiz.correctCount / state.quiz.totalAnswered) * 100)}%`
      : "--";
  const simReport = state.simulator.lastReport;

  const lines = [
    "# Boletim Completo da Sessão — Dinga AI",
    `Data: ${new Date().toLocaleString("pt-BR")}`,
    `Nível CEFR atual: ${el.level.value}`,
    `Objetivo de estudo: ${el.goal.value}`,
    `Consumo estimado da sessão: ${state.totalSessionKb.toFixed(2)} KB (Modo Leve: ${state.lowDataMode ? "Sim" : "Não"})`,
    "Privacidade (LGPD): gerado 100% no navegador, sem persistência de dados pessoais no servidor.",
    "",
    "## Desempenho no Quiz",
    `- Questões respondidas: ${state.quiz.totalAnswered}`,
    `- Acertos: ${state.quiz.correctCount} (${quizAcc})`,
    `- Melhor sequência atual: ${state.quiz.streak}`,
    ""
  ];

  lines.push("## Simulador (Daily / Entrevista)");
  if (simReport) {
    lines.push(`- Modo: ${state.simulator.mode === "interview" ? "Entrevista Técnica" : "Daily Standup"}`);
    lines.push(`- Nota Geral: **${simReport.overallScore}/100** (Clareza: ${simReport.clarityScore} · Gramática: ${simReport.grammarScore} · Vocab. Técnico: ${simReport.technicalVocabScore})`);
    lines.push(`- Resumo: ${simReport.summary}`);
    if (simReport.recommendedNextStep) {
      lines.push(`- Próximo passo: ${simReport.recommendedNextStep}`);
    }
  } else {
    lines.push("- Nenhuma rodada completa registrada nesta sessão.");
  }
  lines.push("");

  lines.push(`## Termos Aprendidos — Caderno de Vocabulário SRS (${state.vocab.items.length})`);
  if (state.vocab.items.length) {
    state.vocab.items.forEach((item) => {
      lines.push(`- **"${item.english}"** — ${item.meaning} *(Box ${item.box}/4 · ${item.context})*`);
    });
  } else {
    lines.push("- Nenhum termo salvo no caderno ainda.");
  }
  lines.push("");

  lines.push(`## Interações com o Tutor IA (${state.history.length})`);
  if (state.history.length) {
    state.history.forEach((item, idx) => {
      lines.push(`### Interação ${idx + 1}`);
      lines.push(`**Estudante:** ${item.question}`);
      lines.push(`**Skill:** ${item.response.activeSkill.name} (${item.response.mtg.watermarkId})`);
      lines.push(`**Resposta:**\n${item.response.reply}`);
      const fontes = (item.response.retrievedSources || []).map((s) => `${s.title} (${s.section})`).join("; ");
      lines.push(`**Fontes RAG:** ${fontes || "Nenhuma"}`);
      lines.push("");
    });
  } else {
    lines.push("- Nenhuma interação no chat registrada nesta sessão.");
  }

  return lines;
}

function exportSummaryForTeacher() {
  const hasAnyData =
    state.history.length > 0 ||
    state.quiz.totalAnswered > 0 ||
    state.vocab.items.length > 0 ||
    Boolean(state.simulator.lastReport);
  if (!hasAnyData) {
    showToast("Pratique no Chat, Quiz, Simulador ou Vocabulário antes de exportar o boletim.");
    return;
  }
  const lines = buildSessionReportLines();
  const blob = new Blob([lines.join("\n")], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "boletim-sessao-dinga-ai.md";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  showToast("Boletim completo exportado (.md).");
}

function printSessionReport() {
  const quizAcc =
    state.quiz.totalAnswered > 0
      ? `${Math.round((state.quiz.correctCount / state.quiz.totalAnswered) * 100)}%`
      : "--";
  const sim = state.simulator.lastReport;
  const vocabRows = state.vocab.items.length
    ? state.vocab.items
        .map(
          (v) =>
            `<tr><td><strong>${escapeHtml(v.english)}</strong></td><td>${escapeHtml(v.meaning)}</td><td>${escapeHtml(v.context)}</td><td>Box ${v.box}/4</td></tr>`
        )
        .join("")
    : `<tr><td colspan="4">Nenhum termo salvo nesta sessão.</td></tr>`;

  const chatRows = state.history.length
    ? state.history
        .map(
          (h, i) => `
          <div class="chat-item">
            <p><strong>${i + 1}. Estudante:</strong> ${escapeHtml(h.question)}</p>
            <p><small>Skill: ${escapeHtml(h.response?.activeSkill?.name || "")} · Watermark: ${escapeHtml(h.response?.mtg?.watermarkId || "")}</small></p>
            <div>${renderMiniMarkdown(h.response?.reply || "")}</div>
          </div>`
        )
        .join("")
    : `<p>Nenhuma interação registrada no chat.</p>`;

  const html = `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8" />
<title>Boletim da Sessão — Dinga AI</title>
<style>
  body { font-family: system-ui, -apple-system, sans-serif; color: #0f172a; max-width: 820px; margin: 24px auto; padding: 0 16px; line-height: 1.5; }
  h1, h2 { color: #1d4ed8; margin-bottom: 6px; }
  .meta { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; margin: 12px 0 20px; font-size: 0.9rem; }
  table { width: 100%; border-collapse: collapse; margin: 10px 0 18px; font-size: 0.88rem; }
  th, td { border: 1px solid #cbd5e1; padding: 6px 8px; text-align: left; }
  th { background: #f1f5f9; }
  .chat-item { border-left: 3px solid #2563eb; padding-left: 10px; margin-bottom: 14px; }
  .lgpd { font-size: 0.8rem; color: #475569; margin-top: 24px; border-top: 1px solid #e2e8f0; padding-top: 10px; }
</style>
</head>
<body>
  <h1>Boletim da Sessão — Dinga AI</h1>
  <div class="meta">
    <div><strong>Nível CEFR:</strong> ${escapeHtml(el.level.value)}</div>
    <div><strong>Objetivo:</strong> ${escapeHtml(el.goal.value)}</div>
    <div><strong>Quiz:</strong> ${state.quiz.correctCount}/${state.quiz.totalAnswered} acertos (${escapeHtml(quizAcc)})</div>
    <div><strong>Simulador:</strong> ${sim ? `${sim.overallScore}/100 (Clareza ${sim.clarityScore}, Gramática ${sim.grammarScore}, Vocab. ${sim.technicalVocabScore})` : "Não realizado"}</div>
    <div><strong>Vocabulário SRS:</strong> ${state.vocab.items.length} termos</div>
    <div><strong>Consumo da Sessão:</strong> ${state.totalSessionKb.toFixed(2)} KB</div>
  </div>
  <h2>Termos Aprendidos (Flashcards SRS)</h2>
  <table>
    <thead><tr><th>Termo (EN)</th><th>Significado (PT)</th><th>Contexto</th><th>SRS</th></tr></thead>
    <tbody>${vocabRows}</tbody>
  </table>
  <h2>Interações com o Tutor IA (${state.history.length})</h2>
  ${chatRows}
  <p class="lgpd">Documento gerado 100% no navegador do estudante (LGPD · sem retenção de dados pessoais no servidor) — ${escapeHtml(new Date().toLocaleString("pt-BR"))}.</p>
</body>
</html>`;

  const win = window.open("", "_blank", "width=880,height=720");
  if (!win) {
    showToast("Permita pop-ups para abrir o boletim imprimível ou use Boletim (.md).");
    return;
  }
  win.document.open();
  win.document.write(html);
  win.document.close();
  win.focus();
  setTimeout(() => win.print(), 250);
}

/* ================= MINI-TESTE DE NIVELAMENTO CEFR (4 QUESTÕES) ================= */
const PLACEMENT_QUESTIONS = [
  {
    level: "A1",
    prompt: "Qual frase está correta em inglês para dizer 'Eu tenho 20 anos e estudo tecnologia'?",
    options: [
      "I have 20 years and I study technology.",
      "I am 20 years old and I study technology.",
      "I am have 20 years and studying technology.",
      "My age have 20 years and I studies technology."
    ],
    correctIndex: 1
  },
  {
    level: "A2",
    prompt: "Qual a tradução correta de: \"Actually, I attended a workshop yesterday\"?",
    options: [
      "Atualmente, eu atendi um cliente ontem.",
      "Na verdade, eu participei de um workshop ontem.",
      "Atualmente, eu pretendi fazer um workshop ontem.",
      "Na verdade, eu atendi um telefonema ontem."
    ],
    correctIndex: 1
  },
  {
    level: "B1",
    prompt: "Na documentação do Microsoft Learn, o que significa \"RAG grounds model outputs in curated sources\"?",
    options: [
      "RAG desliga o modelo quando há muitas fontes.",
      "RAG ancora as respostas do modelo em fontes curadas e verificáveis.",
      "RAG traduz o código-fonte automaticamente para português.",
      "RAG aumenta o consumo de banda removendo referências."
    ],
    correctIndex: 1
  },
  {
    level: "B2",
    prompt: "Em uma Daily Standup em inglês, qual frase comunica um impedimento (blocker) com clareza profissional?",
    options: [
      "Yesterday I work and today I have problem.",
      "I am currently blocked on the staging API deployment due to a timeout error, and I will follow up with DevOps.",
      "Actually I am waiting the server because he not work.",
      "I pretend to fix the push pull request tomorrow."
    ],
    correctIndex: 1
  }
];

const PLACEMENT_TRACKS = {
  A1: {
    goal: "Base do Zero",
    trackName: "Trilha A1 — Base do Zero & Apresentação",
    starterPrompt: "Quero começar do zero (A1): quais são as primeiras frases em inglês para me apresentar e estudar tecnologia?"
  },
  A2: {
    goal: "Inglês para Tecnologia / Microsoft Learn",
    trackName: "Trilha A2 — Passado Simples & Falsos Cognatos",
    starterPrompt: "Qual a diferença entre actually e currently? Me dê exemplos práticos."
  },
  B1: {
    goal: "Inglês para Tecnologia / Microsoft Learn",
    trackName: "Trilha B1 — Leitura Técnica & Microsoft Learn (RAG/APIs)",
    starterPrompt: "Como funciona grounding e RAG nos módulos de IA Generativa do Microsoft Learn?"
  },
  B2: {
    goal: "Entrevista e Carreira",
    trackName: "Trilha B2 — Daily Standup & Entrevista Técnica",
    starterPrompt: "Como estruturar uma atualização profissional completa de Daily Standup (Yesterday, Today, Blockers)?"
  }
};

function openPlacementTest() {
  if (!el.placementDialog || !el.placementBody) return;
  state.placement = { step: 0, score: 0, answers: [] };
  renderPlacementQuestion();
  el.placementDialog.showModal();
}

function renderPlacementQuestion() {
  const step = state.placement.step;
  if (step >= PLACEMENT_QUESTIONS.length) {
    renderPlacementResult();
    return;
  }
  const q = PLACEMENT_QUESTIONS[step];
  if (el.placementStepPill) {
    el.placementStepPill.textContent = `${step + 1} / ${PLACEMENT_QUESTIONS.length} · ${q.level}`;
  }
  const optionsHtml = q.options
    .map(
      (opt, idx) => `
      <button type="button" class="quiz-option-btn placement-opt-btn" data-opt-idx="${idx}">
        <strong>${String.fromCharCode(65 + idx)})</strong> ${escapeHtml(opt)}
      </button>`
    )
    .join("");

  el.placementBody.innerHTML = `
    <p class="small muted" style="margin:0 0 8px">Questão ${step + 1} de ${PLACEMENT_QUESTIONS.length} (nível ${escapeHtml(q.level)})</p>
    <p style="margin:0 0 12px;font-weight:600">${escapeHtml(q.prompt)}</p>
    <div class="quiz-options">${optionsHtml}</div>
  `;

  el.placementBody.querySelectorAll(".placement-opt-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const selected = Number(btn.dataset.optIdx);
      const isCorrect = selected === q.correctIndex;
      if (isCorrect) {
        state.placement.score += 1;
      }
      state.placement.answers.push({ level: q.level, correct: isCorrect });
      state.placement.step += 1;
      renderPlacementQuestion();
    });
  });
}

function resolveCefrLevelFromPlacement(score) {
  if (score <= 1) return "A1";
  if (score === 2) return "A2";
  if (score === 3) return "B1";
  return "B2";
}

function renderPlacementResult() {
  const score = state.placement.score;
  const level = resolveCefrLevelFromPlacement(score);
  const track = PLACEMENT_TRACKS[level];

  if (el.placementStepPill) {
    el.placementStepPill.textContent = `Resultado · ${level}`;
  }

  // Define automaticamente o nível CEFR detectado
  syncLevelUI(level);
  if (el.goal && track.goal) {
    el.goal.value = track.goal;
    saveLocalProgress();
  }

  el.placementBody.innerHTML = `
    <div class="placement-result-box">
      <div class="brand-title-row" style="justify-content:space-between">
        <span class="pill pill-accent">Nível definido: ${escapeHtml(level)}</span>
        <span class="small muted">${score} de ${PLACEMENT_QUESTIONS.length} acertos</span>
      </div>
      <p style="margin:10px 0 4px;font-weight:600">${escapeHtml(track.trackName)}</p>
      <p class="small muted" style="margin:0 0 12px">Seu perfil foi atualizado automaticamente para <strong>${escapeHtml(level)}</strong>. Quer iniciar a primeira prática recomendada agora?</p>
      <div class="mtg-actions">
        <button class="btn-primary btn-xs placement-start-track-btn" type="button">Iniciar trilha no Tutor</button>
        <button class="btn-chip btn-xs placement-retry-btn" type="button">Refazer teste</button>
      </div>
    </div>
  `;

  el.placementBody.querySelector(".placement-start-track-btn")?.addEventListener("click", () => {
    el.placementDialog?.close();
    activateTab("tutor");
    el.skillSelect.value = "auto";
    submitChat(track.starterPrompt);
  });

  el.placementBody.querySelector(".placement-retry-btn")?.addEventListener("click", () => {
    openPlacementTest();
  });

  showToast(`Nível CEFR definido automaticamente para ${level}.`);
}

/* ================= SIMULADOR DAILY / ENTREVISTA ================= */
const SIM_STEP_LABELS = {
  daily: ["Yesterday", "Today", "Blockers"],
  interview: ["Introduction", "Technical Project", "Problem Solving", "Career & Learning"]
};

function renderSimulatorStepper() {
  if (!el.simStepper) return;
  const labels = SIM_STEP_LABELS[state.simulator.mode] || SIM_STEP_LABELS.daily;
  el.simStepper.innerHTML = labels
    .map((lbl, idx) => {
      const stepNum = idx + 1;
      let cls = "sim-step-pill";
      if (state.simulator.completed || stepNum < state.simulator.currentStep) {
        cls += " done";
      } else if (stepNum === state.simulator.currentStep) {
        cls += " active";
      }
      return `<span class="${cls}">${stepNum}. ${escapeHtml(lbl)}</span>`;
    })
    .join("");
}

function updateSimulatorModeButtons() {
  const isDaily = state.simulator.mode === "daily";
  el.simModeDaily?.classList.toggle("active", isDaily);
  el.simModeDaily?.setAttribute("aria-pressed", String(isDaily));
  el.simModeInterview?.classList.toggle("active", !isDaily);
  el.simModeInterview?.setAttribute("aria-pressed", String(!isDaily));
}

async function startSimulation(mode = "daily") {
  state.simulator.mode = mode === "interview" ? "interview" : "daily";
  state.simulator.history = [];
  state.simulator.completed = false;
  updateSimulatorModeButtons();
  if (el.simFeedbackArea) {
    el.simFeedbackArea.hidden = true;
    el.simFeedbackArea.innerHTML = "";
  }
  if (el.simReportArea) {
    el.simReportArea.hidden = true;
    el.simReportArea.innerHTML = "";
  }
  if (el.simStageCard) el.simStageCard.hidden = false;
  if (el.simAnswerInput) el.simAnswerInput.value = "";

  try {
    const res = await fetch("/api/simulator/start", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: state.simulator.mode, level: el.level?.value || "B1" })
    });
    if (!res.ok) {
      const errBody = await res.json().catch(() => null);
      throw new Error(errBody?.message || `HTTP ${res.status}`);
    }
    const data = await res.json();
    applySimulationStep(data);
  } catch (err) {
    showToast(`Falha ao iniciar simulador: ${err.message}`);
  }
}

function applySimulationStep(data) {
  state.simulator.currentStep = data.currentStep;
  state.simulator.totalSteps = data.totalSteps;
  state.simulator.completed = Boolean(data.completed);
  state.simulator.stepLabel = data.stepLabel;
  state.simulator.question = data.question;
  state.simulator.hint = data.hint;
  state.simulator.exampleAnswer = data.exampleAnswer;

  renderSimulatorStepper();

  if (el.simStepBadge) {
    el.simStepBadge.textContent = `Etapa ${data.currentStep} / ${data.totalSteps} · ${data.stepLabel}`;
  }
  if (el.simQuestionText) {
    el.simQuestionText.textContent = data.question;
  }
  if (el.simHintText) {
    el.simHintText.textContent = data.hint;
  }
  if (el.simSubmitBtn) {
    el.simSubmitBtn.textContent =
      data.currentStep >= data.totalSteps ? "Finalizar e gerar boletim" : "Avaliar e avançar";
  }
}

function renderTurnEvaluation(evalData, executionMode, piiMasked) {
  if (!el.simFeedbackArea || !evalData) return;
  el.simFeedbackArea.hidden = false;
  const lgpdTag = piiMasked ? `<span class="pill pill-warn">LGPD Ativa</span>` : "";
  el.simFeedbackArea.innerHTML = `
    <div class="card-block">
      <div class="brand-title-row" style="justify-content:space-between">
        <div class="brand-title-row">
          <span class="pill pill-accent">Avaliação · ${escapeHtml(evalData.stepLabel)}</span>
          ${lgpdTag}
        </div>
        <span class="small muted">${escapeHtml(executionMode || "")}</span>
      </div>
      <div class="sim-scores-grid">
        <div class="sim-score-box"><strong>${evalData.clarityScore}</strong><span>Clareza</span></div>
        <div class="sim-score-box"><strong>${evalData.grammarScore}</strong><span>Gramática</span></div>
        <div class="sim-score-box"><strong>${evalData.technicalVocabScore}</strong><span>Vocab. Técnico</span></div>
      </div>
      <div class="msg-body">${renderMiniMarkdown(evalData.feedback)}</div>
      <p class="small" style="margin:8px 0">
        <strong>Versão natural:</strong> <em>"${escapeHtml(evalData.naturalVersion)}"</em>
      </p>
      <div class="mtg-actions">
        <button class="btn-chip btn-xs sim-speak-natural-btn" type="button">🔊 Ouvir versão natural</button>
        <button class="btn-chip btn-xs sim-save-natural-btn" type="button">+ Salvar no Caderno</button>
      </div>
    </div>
  `;

  el.simFeedbackArea.querySelector(".sim-speak-natural-btn")?.addEventListener("click", (e) => {
    speakEnglishExamples([evalData.naturalVersion], e.currentTarget);
  });
  el.simFeedbackArea.querySelector(".sim-save-natural-btn")?.addEventListener("click", (e) => {
    const added = addVocabItem(
      evalData.naturalVersion,
      evalData.feedback.replace(/[*_`]/g, ""),
      `Simulador · ${evalData.stepLabel}`
    );
    e.currentTarget.textContent = "✓ Salvo";
    showToast(added ? "Frase salva no Caderno de Vocabulário." : "Frase já estava no Caderno.");
  });
}

function renderFinalReport(report, executionMode) {
  if (!el.simReportArea || !report) return;
  if (el.simStageCard) el.simStageCard.hidden = true;
  el.simReportArea.hidden = false;

  const strengthsHtml = (report.strengths || []).map((s) => `<li>${escapeHtml(s)}</li>`).join("");
  const improvementsHtml = (report.improvements || []).map((s) => `<li>${escapeHtml(s)}</li>`).join("");

  el.simReportArea.innerHTML = `
    <div class="sim-report-card">
      <div class="brand-title-row" style="justify-content:space-between">
        <h3 style="margin:0">Mini-Boletim Final</h3>
        <span class="pill pill-accent">Nota Geral: ${report.overallScore}/100</span>
      </div>
      <p class="small" style="margin:8px 0">${escapeHtml(report.summary)}</p>
      <div class="sim-scores-grid">
        <div class="sim-score-box"><strong>${report.clarityScore}</strong><span>Clareza</span></div>
        <div class="sim-score-box"><strong>${report.grammarScore}</strong><span>Gramática</span></div>
        <div class="sim-score-box"><strong>${report.technicalVocabScore}</strong><span>Vocab. Técnico</span></div>
        <div class="sim-score-box"><strong>${report.overallScore}</strong><span>Geral</span></div>
      </div>
      <div class="two-col mt-sm">
        <div>
          <strong class="small">Pontos fortes</strong>
          <ul class="clean-list small">${strengthsHtml}</ul>
        </div>
        <div>
          <strong class="small">Pontos de evolução</strong>
          <ul class="clean-list small">${improvementsHtml || "<li>Continue mantendo esse nível de consistência!</li>"}</ul>
        </div>
      </div>
      <p class="small muted" style="margin:10px 0 0">
        <strong>Próximo passo:</strong> ${escapeHtml(report.recommendedNextStep)} · ${escapeHtml(executionMode || "")}
      </p>
    </div>
  `;
}

function setupSimulator() {
  el.simModeDaily?.addEventListener("click", () => startSimulation("daily"));
  el.simModeInterview?.addEventListener("click", () => startSimulation("interview"));
  el.simRestartBtn?.addEventListener("click", () => startSimulation(state.simulator.mode));

  el.simSpeakQuestionBtn?.addEventListener("click", (e) => {
    if (state.simulator.question) {
      speakEnglishExamples([state.simulator.question], e.currentTarget);
    }
  });

  el.simFillExampleBtn?.addEventListener("click", () => {
    if (el.simAnswerInput && state.simulator.exampleAnswer) {
      el.simAnswerInput.value = state.simulator.exampleAnswer;
      el.simAnswerInput.focus();
    }
  });

  el.simAnswerForm?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const answer = (el.simAnswerInput?.value || "").trim();
    if (!answer) return;

    if (el.simSubmitBtn) {
      el.simSubmitBtn.disabled = true;
      el.simSubmitBtn.textContent = "Avaliando...";
    }

    try {
      const res = await fetch("/api/simulator/answer", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          mode: state.simulator.mode,
          level: el.level?.value || "B1",
          stepIndex: state.simulator.currentStep,
          answer,
          history: state.simulator.history
        })
      });
      if (!res.ok) {
        const errBody = await res.json().catch(() => null);
        throw new Error(errBody?.message || `HTTP ${res.status}`);
      }
      const data = await res.json();
      if (data.turnEvaluation) {
        state.simulator.history.push({
          stepIndex: data.turnEvaluation.stepIndex,
          stepLabel: data.turnEvaluation.stepLabel,
          question: state.simulator.question,
          answer,
          clarityScore: data.turnEvaluation.clarityScore,
          grammarScore: data.turnEvaluation.grammarScore,
          technicalVocabScore: data.turnEvaluation.technicalVocabScore,
          feedback: data.turnEvaluation.feedback,
          naturalVersion: data.turnEvaluation.naturalVersion
        });
        renderTurnEvaluation(data.turnEvaluation, data.executionMode, data.piiMasked);
        recordLearnerSimulationOutcome(data.turnEvaluation, state.simulator.mode);
      }

      if (el.simAnswerInput) el.simAnswerInput.value = "";
      applySimulationStep(data);

      if (data.completed && data.finalReport) {
        state.simulator.lastReport = data.finalReport;
        renderFinalReport(data.finalReport, data.executionMode);
        showToast(`Simulação concluída! Nota geral: ${data.finalReport.overallScore}/100.`);
      }
    } catch (err) {
      showToast(`Erro no simulador: ${err.message}`);
    } finally {
      if (el.simSubmitBtn) {
        el.simSubmitBtn.disabled = false;
        el.simSubmitBtn.textContent =
          state.simulator.currentStep >= state.simulator.totalSteps
            ? "Finalizar e gerar boletim"
            : "Avaliar e avançar";
      }
    }
  });
}

/* ================= CADERNO DE VOCABULÁRIO (FLASHCARDS SRS) ================= */
const SRS_INTERVALS_MS = {
  1: 60 * 1000,            // 1 min
  2: 10 * 60 * 1000,       // 10 min
  3: 24 * 60 * 60 * 1000,  // 1 dia
  4: 3 * 24 * 60 * 60 * 1000 // 3 dias
};

function isVocabSaved(englishPhrase) {
  const norm = String(englishPhrase || "").trim().toLowerCase();
  return state.vocab.items.some((item) => item.english.toLowerCase() === norm);
}

function addVocabItem(english, meaning, context = "Prática Dinga AI") {
  const cleanEn = String(english || "").trim();
  if (!cleanEn) return false;
  if (isVocabSaved(cleanEn)) return false;

  state.vocab.items.unshift({
    id: `voc-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    english: cleanEn,
    meaning: String(meaning || "Expressão salva para revisão ativa.").trim(),
    context: String(context || "Dinga AI").trim(),
    box: 1,
    nextReviewAt: Date.now(),
    reviews: 0,
    createdAt: new Date().toISOString()
  });
  saveVocabToStorage();
  renderVocab();
  return true;
}

function getDueVocabItems() {
  const now = Date.now();
  return state.vocab.items.filter((item) => (item.nextReviewAt || 0) <= now);
}

function renderVocab() {
  const total = state.vocab.items.length;
  const due = getDueVocabItems();

  if (el.tabVocabCount) {
    el.tabVocabCount.textContent = String(total);
  }
  if (el.vocabStatsText) {
    el.vocabStatsText.textContent = `${total} termo${total === 1 ? "" : "s"} salvo${total === 1 ? "" : "s"} · ${due.length} para revisar agora`;
  }
  if (el.vocabReviewBtn) {
    el.vocabReviewBtn.disabled = total === 0;
    el.vocabReviewBtn.textContent = due.length > 0 ? `Revisar agora (${due.length})` : `Revisar todos (${total})`;
  }
  if (el.vocabExportCsvBtn) {
    el.vocabExportCsvBtn.disabled = total === 0;
  }

  if (!el.vocabList) return;
  if (!total) {
    el.vocabList.innerHTML = `<p class="muted small">Nenhum termo salvo ainda. Clique no botão <strong>+</strong> ao lado dos chips de pronúncia no Chat, no Quiz ou no Simulador para montar seu caderno SRS.</p>`;
    if (el.vocabFlashcardStage) el.vocabFlashcardStage.hidden = true;
    return;
  }

  const query = (state.vocab.searchQuery || "").trim().toLowerCase();
  const visibleItems = query
    ? state.vocab.items.filter(
        (item) =>
          (item.english || "").toLowerCase().includes(query) ||
          (item.meaning || "").toLowerCase().includes(query) ||
          (item.context || "").toLowerCase().includes(query)
      )
    : state.vocab.items;

  if (!visibleItems.length) {
    el.vocabList.innerHTML = `<p class="muted small">Nenhum termo encontrado para "<strong>${escapeHtml(state.vocab.searchQuery)}</strong>".</p>`;
    return;
  }

  el.vocabList.innerHTML = "";
  visibleItems.forEach((item) => {
    const isDue = (item.nextReviewAt || 0) <= Date.now();
    const card = document.createElement("article");
    card.className = "card-block vocab-item";
    card.innerHTML = `
      <div>
        <div class="brand-title-row" style="justify-content:space-between">
          <span class="pill ${isDue ? "pill-accent" : ""}">Box ${item.box}/4${isDue ? " · Revisar" : ""}</span>
          <span class="small muted">${escapeHtml(item.context)}</span>
        </div>
        <p style="margin:8px 0 4px;font-weight:600">"${escapeHtml(item.english)}"</p>
        <p class="small muted" style="margin:0">${escapeHtml(item.meaning)}</p>
      </div>
      <div class="mtg-actions">
        <button class="btn-chip btn-xs vocab-speak-btn" type="button">🔊 Ouvir</button>
        <button class="btn-ghost btn-xs btn-danger-outline vocab-del-btn" type="button">Remover</button>
      </div>
    `;
    card.querySelector(".vocab-speak-btn").addEventListener("click", (e) => {
      speakEnglishExamples([item.english], e.currentTarget);
    });
    card.querySelector(".vocab-del-btn").addEventListener("click", () => {
      state.vocab.items = state.vocab.items.filter((v) => v.id !== item.id);
      const removedQueueIdx = state.vocab.reviewQueue.findIndex((v) => v.id === item.id);
      if (removedQueueIdx >= 0) {
        state.vocab.reviewQueue.splice(removedQueueIdx, 1);
        if (removedQueueIdx < state.vocab.reviewIndex) {
          state.vocab.reviewIndex = Math.max(0, state.vocab.reviewIndex - 1);
        } else if (removedQueueIdx === state.vocab.reviewIndex) {
          state.vocab.revealed = false;
        }
      }
      saveVocabToStorage();
      renderVocab();
      if (el.vocabFlashcardStage && !el.vocabFlashcardStage.hidden) {
        renderVocabFlashcard();
      }
      showToast("Termo removido do Caderno.");
    });
    el.vocabList.appendChild(card);
  });
}

function renderVocabFlashcard() {
  if (!el.vocabFlashcardStage) return;
  const queue = state.vocab.reviewQueue;
  if (!queue.length || state.vocab.reviewIndex >= queue.length) {
    el.vocabFlashcardStage.innerHTML = `
      <div class="flashcard-card">
        <span class="pill pill-accent">Revisão concluída!</span>
        <p class="small" style="margin:10px 0">Você revisou todos os cartões da fila SRS.</p>
        <button class="btn-chip btn-xs vocab-close-stage-btn" type="button">Fechar</button>
      </div>
    `;
    el.vocabFlashcardStage.querySelector(".vocab-close-stage-btn")?.addEventListener("click", () => {
      el.vocabFlashcardStage.hidden = true;
    });
    return;
  }

  const card = queue[state.vocab.reviewIndex];
  el.vocabFlashcardStage.hidden = false;
  el.vocabFlashcardStage.innerHTML = `
    <div class="flashcard-card">
      <div class="brand-title-row" style="justify-content:space-between">
        <span class="pill pill-accent">Flashcard ${state.vocab.reviewIndex + 1} / ${queue.length}</span>
        <span class="pill">Box SRS ${card.box}/4</span>
      </div>
      <div class="flashcard-front">"${escapeHtml(card.english)}"</div>
      <div class="srs-actions">
        <button class="btn-chip btn-xs fc-speak-btn" type="button">🔊 Ouvir pronúncia</button>
        ${!state.vocab.revealed ? `<button class="btn-primary btn-xs fc-reveal-btn" type="button">Mostrar verso (Espaço)</button>` : ""}
      </div>
      ${
        state.vocab.revealed
          ? `
        <div class="flashcard-back">
          <p class="small" style="margin:0 0 4px"><strong>Significado / Dica:</strong> ${escapeHtml(card.meaning)}</p>
          <p class="small muted" style="margin:0"><strong>Contexto técnico:</strong> ${escapeHtml(card.context)}</p>
          <div class="srs-actions">
            <button class="btn-chip btn-xs fc-rate-btn" data-rating="hard" type="button">Difícil (1 min)</button>
            <button class="btn-chip btn-xs fc-rate-btn" data-rating="good" type="button">Bom</button>
            <button class="btn-primary btn-xs fc-rate-btn" data-rating="easy" type="button">Fácil</button>
          </div>
        </div>`
          : ""
      }
    </div>
  `;

  el.vocabFlashcardStage.querySelector(".fc-speak-btn")?.addEventListener("click", (e) => {
    speakEnglishExamples([card.english], e.currentTarget);
  });
  el.vocabFlashcardStage.querySelector(".fc-reveal-btn")?.addEventListener("click", () => {
    state.vocab.revealed = true;
    renderVocabFlashcard();
  });
  el.vocabFlashcardStage.querySelectorAll(".fc-rate-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const rating = btn.dataset.rating;
      const target = state.vocab.items.find((v) => v.id === card.id);
      if (target) {
        if (rating === "hard") {
          target.box = 1;
        } else if (rating === "good") {
          target.box = Math.min(4, (target.box || 1) + 1);
        } else {
          target.box = Math.min(4, (target.box || 1) + 2);
        }
        target.reviews = (target.reviews || 0) + 1;
        target.nextReviewAt = Date.now() + (SRS_INTERVALS_MS[target.box] || SRS_INTERVALS_MS[1]);
        saveVocabToStorage();
      }
      state.vocab.reviewIndex += 1;
      state.vocab.revealed = false;
      renderVocab();
      renderVocabFlashcard();
    });
  });
}

function setupVocab() {
  el.vocabSearchInput?.addEventListener("input", (e) => {
    state.vocab.searchQuery = e.target.value || "";
    renderVocab();
  });

  el.vocabExportCsvBtn?.addEventListener("click", exportVocabToAnkiCsv);

  el.vocabReviewBtn?.addEventListener("click", () => {
    const due = getDueVocabItems();
    state.vocab.reviewQueue = due.length > 0 ? [...due] : [...state.vocab.items];
    state.vocab.reviewIndex = 0;
    state.vocab.revealed = false;
    renderVocabFlashcard();
  });

  el.vocabAddForm?.addEventListener("submit", (e) => {
    e.preventDefault();
    const en = el.vocabInputEn?.value || "";
    const meaning = el.vocabInputMeaning?.value || "";
    const ctx = el.vocabInputContext?.value || "Adicionado manualmente";
    const added = addVocabItem(en, meaning, ctx);
    if (added) {
      if (el.vocabInputEn) el.vocabInputEn.value = "";
      if (el.vocabInputMeaning) el.vocabInputMeaning.value = "";
      if (el.vocabInputContext) el.vocabInputContext.value = "";
      showToast("Termo salvo no Caderno de Vocabulário.");
    } else {
      showToast("Este termo já está no Caderno.");
    }
  });
}

/* ================= MÉTRICAS DA SESSÃO & GOVERNANÇA ================= */
async function refreshSessionMetrics() {
  if (!el.metricsGrid) return;
  try {
    const res = await fetch("/api/metrics");
    if (!res.ok) {
      const errBody = await res.json().catch(() => null);
      throw new Error(errBody?.message || `HTTP ${res.status}`);
    }
    const m = await res.json();

    const quizLabel = m.totalQuizAnswered > 0 ? `${m.quizAccuracyPercent}%` : "--%";
    const quizSub = `${m.totalQuizCorrect}/${m.totalQuizAnswered} acertos no Quiz`;
    const guardrailSub = `${m.lgpdMaskCount} LGPD · ${m.antiPlagiarismCount} Anti-Plágio`;
    const savedLabel = m.estimatedSavedKb > 0 ? `${m.estimatedSavedKb.toFixed(1)} KB` : "0 KB";
    const savedSub = `${m.estimatedSavedPercent}% economia (${m.lowDataChatTurns} turnos Modo Leve)`;
    const turnsSub = `${m.totalChatTurns} chats · ${m.simulationCompletedCount} simulações`;

    el.metricsGrid.innerHTML = `
      <div class="metric-tile">
        <span>Acerto no Quiz</span>
        <strong>${escapeHtml(quizLabel)}</strong>
        <span>${escapeHtml(quizSub)}</span>
      </div>
      <div class="metric-tile">
        <span>Guardrails Ativos</span>
        <strong>${m.lgpdMaskCount + m.antiPlagiarismCount}</strong>
        <span>${escapeHtml(guardrailSub)}</span>
      </div>
      <div class="metric-tile">
        <span>Economia de Dados</span>
        <strong>${escapeHtml(savedLabel)}</strong>
        <span>${escapeHtml(savedSub)}</span>
      </div>
      <div class="metric-tile">
        <span>Atividade da Sessão</span>
        <strong>${m.totalChatTurns + m.simulationTurnsCount}</strong>
        <span>${escapeHtml(turnsSub)}</span>
      </div>
    `;

    if (el.metricsBreakdown) {
      const pills = [];
      Object.entries(m.skillUsage || {}).forEach(([skillId, count]) => {
        pills.push(`<span class="pill pill-accent">Skill ${escapeHtml(skillId)}: ${count}</span>`);
      });
      Object.entries(m.quizByLevel || {}).forEach(([lvl, st]) => {
        pills.push(`<span class="pill">Quiz ${escapeHtml(lvl)}: ${st.accuracyPercent}% (${st.correct}/${st.answered})</span>`);
      });
      Object.entries(m.quizByCategory || {}).forEach(([cat, st]) => {
        pills.push(`<span class="pill">${escapeHtml(cat)}: ${st.accuracyPercent}%</span>`);
      });
      if (m.rateLimitBlockedCount > 0) {
        pills.push(`<span class="pill pill-warn">Rate-limit: ${m.rateLimitBlockedCount}</span>`);
      }
      el.metricsBreakdown.innerHTML = pills.length
        ? pills.join("")
        : `<span class="small muted">Envie uma pergunta no Chat, responda ao Quiz ou rode o Simulador para popular os indicadores.</span>`;
    }
  } catch {
    el.metricsGrid.innerHTML = `<p class="muted small">Não foi possível carregar métricas agora.</p>`;
  }
}

async function bootstrap() {
  loadLocalProgress();
  loadVocabFromStorage();
  setupTabs();
  setupA11yAndShortcuts();
  setupTeacherRagPanel();
  setupQuizControls();
  setupSimulator();
  setupVocab();
  el.refreshMetricsBtn?.addEventListener("click", refreshSessionMetrics);
  renderWelcomeMessage();
  updateMemoryAndTelemetryBar();
  renderVocab();

  el.chatForm.addEventListener("submit", (e) => {
    e.preventDefault();
    submitChat(el.chatInput.value);
  });

  el.knowledgeFilter.addEventListener("input", (e) => {
    renderKnowledge(state.knowledge.documents, e.target.value);
  });

  el.cancelFeedback.addEventListener("click", () => el.feedbackDialog.close());
  el.feedbackForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    await fetch("/api/feedback", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        watermarkId: el.feedbackWatermark.textContent,
        category: el.feedbackCategory.value,
        comment: el.feedbackComment.value
      })
    });
    el.feedbackDialog.close();
    await refreshFeedbackList();
    showToast("Relato registrado.");
  });

  const [metaRes, knowledgeRes] = await Promise.all([fetch("/api/meta"), fetch("/api/knowledge")]);
  state.meta = await metaRes.json();
  state.knowledge = await knowledgeRes.json();

  el.modePill.textContent = state.meta.remoteLlmEnabled
    ? `IA + RAG (${state.meta.llmProvider})`
    : "RAG Local";

  renderSkillsAndDemoPrompts(state.meta.skills);
  renderKnowledge(state.knowledge.documents);
  setQuizViewMode(state.quiz.viewMode);
  await Promise.all([refreshFeedbackList(), loadQuizQuestions(), refreshSessionMetrics()]);
}

bootstrap();
