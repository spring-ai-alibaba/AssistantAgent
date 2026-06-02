/* ═══════════════════════════════════════════════════════
   Experience Console — Application Logic
   ═══════════════════════════════════════════════════════ */

const ExpConsole = (() => {
  'use strict';

  // ─── Configuration ───
  const API_BASE = '/exp-console/api';
  const GLOBAL_TENANT_ID = 'global';
  const TOOL_INVOCATION_PATH_PROPERTY = 'toolInvocationPath';
  const TOOL_INVOCATION_PATH_DEFAULT = 'CODE_ONLY';

  // ─── State ───
  const state = {
    activeTab: 'ALL',
    experiences: [],
    stats: { REACT: 0, COMMON: 0, TOOL: 0 },
    pagination: { page: 1, size: 20, total: 0 },
    searchQuery: '',
    isSearchMode: false,
    toolSources: [],
    expandedSource: null,
    sourceTools: {},
    editingId: null,
    deleteTarget: null,
    loading: false,
    tenantOptions: [],
    selectedTenant: '',
    includeGlobal: true,
    formTenantSelections: [],
    locale: navigator.language && navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en',
  };

  const I18N = {
    en: {
      pageTitle: 'Experience Console',
      headerTitle: 'Experience Console',
      tenant: 'Tenant',
      allTenants: 'All Tenants',
      includeGlobal: 'Include Global',
      importSkill: 'Import SKILL',
      newExperience: 'New Experience',
      tabAll: 'All',
      tabReact: 'React',
      tabCommon: 'Common',
      tabTool: 'Tool',
      tabSources: 'Tool Sources',
      exportAll: 'Export All',
      searchPlaceholder: 'Search experiences...',
      searchPlaceholderDisabled: '',
      pageOf: 'Page {page} of {totalPages}',
      prev: 'Prev',
      next: 'Next',
      noToolSources: 'No tool sources',
      noToolSourcesConfigured: 'No tool sources are configured.',
      noToolsAvailable: 'No tools available.',
      selectAll: 'Select All',
      deselectAll: 'Deselect All',
      imported: 'Imported',
      importSelected: 'Import Selected ({count})',
      syncSource: 'Sync Source',
      availableCount: '{count} available',
      importedTotal: '{imported} imported / {total} total',
      newExperienceModal: 'New Experience',
      editExperienceModal: 'Edit Experience',
      save: 'Save',
      cancel: 'Cancel',
      close: 'Close',
      delete: 'Delete',
      confirmDeleteTitle: 'Confirm Delete',
      type: 'Type',
      selectType: 'Select type...',
      name: 'Name',
      description: 'Description',
      content: 'Content',
      tags: 'Tags',
      commaSeparated: 'comma-separated',
      tenantIds: 'Tenant ID(s)',
      toolInvocationPath: 'Tool Invocation Path',
      tenantHint: 'comma-separated, GLOBAL means global',
      tenantHintMulti: 'multi-select, GLOBAL means global',
      tenantSelectPlaceholder: 'Select tenant(s)...',
      tenantSearchPlaceholder: 'Search tenant...',
      tenantNoOptions: 'No tenant options',
      tenantNoMatches: 'No matching tenants',
      disclosureStrategy: 'Disclosure Strategy',
      defaultNone: 'Default / None',
      associatedTools: 'Associated Tools',
      relatedExperiences: 'Related Experiences',
      relatedExperiencesHint: 'comma-separated IDs',
      fastIntent: 'Fast Intent',
      enabled: 'Enabled',
      disabled: 'Disabled',
      priority: 'Priority',
      mode: 'Mode',
      fallback: 'Fallback',
      fastIntentMatchJson: 'Fast Intent Match JSON',
      artifactJson: 'Artifact JSON',
      referencesLabel: 'References',
      assetsLabel: 'Assets',
      resummarizeBtn: 'Re-summarize',
      resummarizeOk: 'Updated {n} references',
      resummarizeFail: 'Re-summarize failed',
      propertiesJson: 'Metadata Properties JSON',
      importSkillTitle: 'Import SKILL',
      importExperienceModal: 'Import Experience (from SKILL)',
      pasteSkillMarkdown: 'Paste SKILL markdown content',
      uploadPackage: 'Upload Package',
      pasteMarkdown: 'Paste Markdown',
      uploadDropzoneText: 'Drag & drop a .tgz or .zip skill package here',
      uploadDropzoneHint: 'or click to browse',
      importResultTitle: 'Import Result',
      processedFiles: 'Processed',
      skippedFiles: 'Skipped',
      warnings: 'Warnings',
      toastNoFileSelected: 'Please select a skill package file.',
      preview: 'Preview',
      import: 'Import',
      exportSkillTitle: 'Export SKILL',
      copyToClipboard: 'Copy to Clipboard',
      previewType: 'Type',
      previewName: 'Name',
      previewDescription: 'Description',
      previewTags: 'Tags',
      notAvailable: '—',
      toastLoadFailed: 'Failed to load experiences: {message}',
      toastLoadExperienceFailed: 'Failed to load experience: {message}',
      toastSaveFailed: 'Save failed: {message}',
      toastDeleteFailed: 'Delete failed: {message}',
      toastImportFailed: 'Import failed: {message}',
      toastPreviewFailed: 'Preview failed: {message}',
      toastExportFailed: 'Export failed: {message}',
      toastExportDownloaded: 'Downloaded {filename}.',
      toastLoadToolsFailed: 'Failed to load tools: {message}',
      toastRequestFailed: 'Request failed ({status})',
      toastUpdated: 'Experience updated.',
      toastCreated: 'Experience created.',
      toastDeleted: 'Experience deleted.',
      toastSourceSynced: 'Sync complete: {created} created, {updated} updated, {deleted} deleted.',
      toastSourceSyncFailed: 'Sync failed: {message}',
      toastSourceSyncPartial: 'Sync finished with {count} issue(s).',
      toastSkillImported: 'SKILL imported successfully.',
      toastCopied: 'Copied to clipboard.',
      toastNameRequired: 'Name is required.',
      toastContentRequired: 'Content is required.',
      toastTypeRequired: 'Type is required.',
      toastPasteSkillFirst: 'Please paste SKILL markdown content.',
      toastJsonInvalid: '{fieldLabel} must be valid JSON',
      fieldArtifactJson: 'Artifact JSON',
      fieldPropertiesJson: 'Metadata Properties JSON',
      fieldFastIntentMatchJson: 'Fast Intent Match JSON',
      emptySearchResult: 'No results for "{query}"',
      emptyTypeResult: 'No {type} experiences yet',
      emptyAnyResult: 'No experiences yet',
      emptySearchHint: 'Try adjusting your search query.',
      emptyCreateHint: 'Create your first experience to get started.',
      deleteConfirm: 'Delete "{name}"? This action cannot be undone.',
      globalTag: 'GLOBAL',
      tenantTag: 'TENANT:{tenantId}',
      fastTag: 'FAST:{priority}',
      toolsTag: 'TOOLS:{count}',
      justNow: 'just now',
      minutesAgo: '{value}m ago',
      hoursAgo: '{value}h ago',
      daysAgo: '{value}d ago',
      exportAllTitle: 'Export All — {type}',
      toolsImportedCount: '{count} tool(s) imported.',
    },
    zh: {
      pageTitle: '经验管理后台',
      headerTitle: '经验管理后台',
      tenant: '租户',
      allTenants: '全部租户',
      includeGlobal: '包含全局',
      importSkill: '导入 SKILL',
      newExperience: '新建经验',
      tabAll: '全部',
      tabReact: 'React',
      tabCommon: 'Common',
      tabTool: 'Tool',
      tabSources: '工具来源',
      exportAll: '批量导出',
      searchPlaceholder: '搜索经验...',
      searchPlaceholderDisabled: '',
      pageOf: '第 {page} / {totalPages} 页',
      prev: '上一页',
      next: '下一页',
      noToolSources: '暂无工具来源',
      noToolSourcesConfigured: '当前未配置任何工具来源。',
      noToolsAvailable: '暂无可用工具。',
      selectAll: '全选',
      deselectAll: '取消全选',
      imported: '已导入',
      importSelected: '导入所选 ({count})',
      syncSource: '同步来源',
      availableCount: '{count} 可选',
      importedTotal: '已导入 {imported} / 共 {total}',
      newExperienceModal: '新建经验',
      editExperienceModal: '编辑经验',
      save: '保存',
      cancel: '取消',
      close: '关闭',
      delete: '删除',
      confirmDeleteTitle: '确认删除',
      type: '类型',
      selectType: '请选择类型...',
      name: '名称',
      description: '描述',
      content: '内容',
      tags: '标签',
      commaSeparated: '逗号分隔',
      tenantIds: '租户 ID',
      toolInvocationPath: '工具调用模式',
      tenantHint: '逗号分隔，GLOBAL 表示全局',
      tenantHintMulti: '支持多选，GLOBAL 表示全局',
      tenantSelectPlaceholder: '请选择租户（可多选）...',
      tenantSearchPlaceholder: '搜索租户...',
      tenantNoOptions: '暂无租户选项',
      tenantNoMatches: '没有匹配的租户',
      disclosureStrategy: '披露策略',
      defaultNone: '默认 / 无',
      associatedTools: '关联工具',
      relatedExperiences: '关联经验',
      relatedExperiencesHint: '逗号分隔 ID',
      fastIntent: '快速意图',
      enabled: '启用',
      disabled: '禁用',
      priority: '优先级',
      mode: '模式',
      fallback: '回退策略',
      fastIntentMatchJson: '快速意图匹配 JSON',
      artifactJson: 'Artifact JSON',
      referencesLabel: '参考文档 References',
      assetsLabel: '资源文件 Assets',
      resummarizeBtn: '重新总结',
      resummarizeOk: '已更新 {n} 个参考文档描述',
      resummarizeFail: '重新总结失败',
      propertiesJson: '元数据属性 JSON',
      importSkillTitle: '导入 SKILL',
      importExperienceModal: '导入经验（来自 SKILL）',
      pasteSkillMarkdown: '粘贴 SKILL Markdown 内容',
      uploadPackage: '上传技能包',
      pasteMarkdown: '粘贴 Markdown',
      uploadDropzoneText: '拖放 .tgz 或 .zip 技能包到此处',
      uploadDropzoneHint: '或点击选择文件',
      importResultTitle: '导入结果',
      processedFiles: '已处理',
      skippedFiles: '已跳过',
      warnings: '警告',
      toastNoFileSelected: '请先选择技能包文件。',
      preview: '预览',
      import: '导入',
      exportSkillTitle: '导出 SKILL',
      copyToClipboard: '复制到剪贴板',
      previewType: '类型',
      previewName: '名称',
      previewDescription: '描述',
      previewTags: '标签',
      notAvailable: '—',
      toastLoadFailed: '加载经验失败: {message}',
      toastLoadExperienceFailed: '加载经验详情失败: {message}',
      toastSaveFailed: '保存失败: {message}',
      toastDeleteFailed: '删除失败: {message}',
      toastImportFailed: '导入失败: {message}',
      toastPreviewFailed: '预览失败: {message}',
      toastExportFailed: '导出失败: {message}',
      toastExportDownloaded: '已下载 {filename}。',
      toastLoadToolsFailed: '加载工具失败: {message}',
      toastRequestFailed: '请求失败 ({status})',
      toastUpdated: '经验已更新。',
      toastCreated: '经验已创建。',
      toastDeleted: '经验已删除。',
      toastSourceSynced: '同步完成：新增 {created}，更新 {updated}，删除 {deleted}。',
      toastSourceSyncFailed: '同步失败: {message}',
      toastSourceSyncPartial: '同步完成，但有 {count} 个问题。',
      toastSkillImported: 'SKILL 导入成功。',
      toastCopied: '已复制到剪贴板。',
      toastNameRequired: '名称不能为空。',
      toastContentRequired: '内容不能为空。',
      toastTypeRequired: '类型不能为空。',
      toastPasteSkillFirst: '请先粘贴 SKILL Markdown 内容。',
      toastJsonInvalid: '{fieldLabel} 必须是合法 JSON',
      fieldArtifactJson: 'Artifact JSON',
      fieldPropertiesJson: '元数据属性 JSON',
      fieldFastIntentMatchJson: '快速意图匹配 JSON',
      emptySearchResult: '未找到 "{query}" 的结果',
      emptyTypeResult: '暂无 {type} 经验',
      emptyAnyResult: '暂无经验',
      emptySearchHint: '尝试更换搜索关键词。',
      emptyCreateHint: '先创建第一条经验吧。',
      deleteConfirm: '确定删除 "{name}" 吗？该操作不可恢复。',
      globalTag: '全局',
      tenantTag: '租户:{tenantId}',
      fastTag: 'FAST:{priority}',
      toolsTag: '工具:{count}',
      justNow: '刚刚',
      minutesAgo: '{value} 分钟前',
      hoursAgo: '{value} 小时前',
      daysAgo: '{value} 天前',
      exportAllTitle: '批量导出 — {type}',
      toolsImportedCount: '已导入 {count} 个工具。',
    },
  };

  // ─── DOM Cache ───
  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => document.querySelectorAll(sel);

  const dom = {};
  function cacheDom() {
    dom.searchInput = $('#search-input');
    dom.tenantFilterWrap = $('#header-filters');
    dom.tenantFilterLabel = $('#tenant-filter-label');
    dom.tenantFilter = $('#tenant-filter');
    dom.includeGlobalFilter = $('#include-global-filter');
    dom.includeGlobalFilterText = $('#include-global-filter-text');
    dom.btnLanguageToggle = $('#btn-language-toggle');
    dom.btnLanguageToggleText = $('#btn-language-toggle-text');
    dom.experienceList = $('#experience-list');
    dom.pagination = $('#pagination');
    dom.pageInfo = $('#page-info');
    dom.btnPrev = $('#btn-prev');
    dom.btnNext = $('#btn-next');
    dom.toolSources = $('#tool-sources');
    dom.toolSourcesList = $('#tool-sources-list');
    dom.editModal = $('#edit-modal');
    dom.modalTitle = $('#modal-title');
    dom.formId = $('#form-id');
    dom.formType = $('#form-type');
    dom.formGroupType = $('#form-group-type');
    dom.formName = $('#form-name');
    dom.formDescription = $('#form-description');
    dom.formContent = $('#form-content');
    dom.formTags = $('#form-tags');
    dom.formTenantIds = $('#form-tenant-ids');
    dom.formTenantMultiSelect = $('#form-tenant-multiselect');
    dom.formTenantTrigger = $('#form-tenant-trigger');
    dom.formTenantTags = $('#form-tenant-tags');
    dom.formTenantPlaceholder = $('#form-tenant-placeholder');
    dom.formTenantDropdown = $('#form-tenant-dropdown');
    dom.formTenantSearch = $('#form-tenant-search');
    dom.formTenantOptions = $('#form-tenant-options');
    dom.formDisclosureStrategy = $('#form-disclosure-strategy');
    dom.formToolInvocationPath = $('#form-tool-invocation-path');
    dom.formGroupToolInvocationPath = $('#form-group-tool-invocation-path');
    dom.formAssociatedTools = $('#form-associated-tools');
    dom.formGroupAssociatedTools = $('#form-group-associated-tools');
    dom.formRelatedExperiences = $('#form-related-experiences');
    dom.formGroupRelatedExperiences = $('#form-group-related-experiences');
    dom.formFastIntentSection = $('#form-fastintent-section');
    dom.formGroupArtifactJson = $('#form-group-artifact-json');
    dom.formGroupPropertiesJson = $('#form-group-properties-json');
    dom.formGroupDisclosureStrategy = $('#form-group-disclosure-strategy');
    dom.formFastIntentEnabled = $('#form-fastintent-enabled');
    dom.formFastIntentPriority = $('#form-fastintent-priority');
    dom.formFastIntentMode = $('#form-fastintent-mode');
    dom.formFastIntentFallback = $('#form-fastintent-fallback');
    dom.formFastIntentMatch = $('#form-fastintent-match');
    dom.formArtifactJson = $('#form-artifact-json');
    dom.formPropertiesJson = $('#form-properties-json');
    dom.formGroupReferences = $('#form-group-references');
    dom.formReferencesList = $('#form-references-list');
    dom.formGroupAssets = $('#form-group-assets');
    dom.formAssetsList = $('#form-assets-list');
    dom.skillImportModal = $('#skill-import-modal');
    dom.skillImportContent = $('#skill-import-content');
    dom.skillPreviewArea = $('#skill-preview-area');
    dom.skillPreviewContent = $('#skill-preview-content');
    dom.skillExportModal = $('#skill-export-modal');
    dom.skillExportContent = $('#skill-export-content');
    // Upload tab elements
    dom.skillUploadDropzone = $('#skill-upload-dropzone');
    dom.skillUploadInput = $('#skill-upload-input');
    dom.uploadFileInfo = $('#upload-file-info');
    dom.uploadFileName = $('#upload-file-name');
    dom.uploadFileClear = $('#upload-file-clear');
    dom.importTabUpload = $('#import-tab-upload');
    dom.importTabPaste = $('#import-tab-paste');
    dom.tabUploadFile = $('#tab-upload-file');
    dom.tabPasteText = $('#tab-paste-text');
    dom.skillImportResultArea = $('#skill-import-result-area');
    dom.skillImportResultContent = $('#skill-import-result-content');
    dom.confirmDialog = $('#confirm-dialog');
    dom.confirmMessage = $('#confirm-message');
    dom.toastContainer = $('#toast-container');
    dom.countAll = $('#count-all');
    dom.countReact = $('#count-react');
    dom.countCommon = $('#count-common');
    dom.countTool = $('#count-tool');
    dom.btnExportAll = $('#btn-export-all');
  }


  // ═══════════════════════════════════════════════════════
  // API Client
  // ═══════════════════════════════════════════════════════

  async function apiFetch(path, options = {}) {
    const url = API_BASE + path;
    const config = {
      headers: { 'Content-Type': 'application/json', ...options.headers },
      ...options,
    };
    if (config.body && typeof config.body === 'object') {
      config.body = JSON.stringify(config.body);
    }
    const res = await fetch(url, config);
    if (!res.ok) {
      let msg = t('toastRequestFailed', { status: res.status });
      try {
        const err = await res.json();
        if (err.error) msg = err.error;
      } catch (_) { /* ignore */ }
      throw new Error(msg);
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }

  async function apiFetchRaw(path, options = {}) {
    const url = API_BASE + path;
    const res = await fetch(url, options);
    if (!res.ok) {
      let msg = t('toastRequestFailed', { status: res.status });
      try {
        const err = await res.json();
        if (err.error) msg = err.error;
      } catch (_) { /* ignore */ }
      throw new Error(msg);
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }

  const api = {
    listExperiences(type, page, size) {
      const params = new URLSearchParams();
      if (type && type !== 'ALL') params.set('type', type);
      if (state.selectedTenant) {
        params.set('tenantId', state.selectedTenant);
        params.set('includeGlobal', shouldIncludeGlobalForRequests() ? 'true' : 'false');
      }
      params.set('page', page);
      params.set('size', size);
      return apiFetch('/experiences?' + params.toString());
    },

    searchExperiences(q, type, topK = 20) {
      const params = new URLSearchParams({ q, topK });
      if (type && type !== 'ALL') params.set('type', type);
      if (state.selectedTenant) {
        params.set('tenantId', state.selectedTenant);
        params.set('includeGlobal', shouldIncludeGlobalForRequests() ? 'true' : 'false');
      }
      return apiFetch('/experiences/search?' + params.toString());
    },

    listTenants() {
      return apiFetch('/tenants');
    },

    getStats() {
      const params = new URLSearchParams();
      if (state.selectedTenant) {
        params.set('tenantId', state.selectedTenant);
        params.set('includeGlobal', shouldIncludeGlobalForRequests() ? 'true' : 'false');
      }
      const queryString = params.toString();
      return apiFetch('/experiences/stats' + (queryString ? ('?' + queryString) : ''));
    },

    getExperience(id) {
      return apiFetch('/experiences/' + encodeURIComponent(id));
    },

    createExperience(data) {
      return apiFetch('/experiences', { method: 'POST', body: data });
    },

    updateExperience(id, data) {
      return apiFetch('/experiences/' + encodeURIComponent(id), { method: 'PUT', body: data });
    },

    deleteExperience(id) {
      return apiFetch('/experiences/' + encodeURIComponent(id), { method: 'DELETE' });
    },

    listToolSources() {
      return apiFetch('/tool-sources');
    },

    listToolsFromSource(sourceId) {
      return apiFetch('/tool-sources/' + encodeURIComponent(sourceId) + '/tools');
    },

    listImportedTools(sourceId) {
      return apiFetch('/tool-sources/' + encodeURIComponent(sourceId) + '/imported');
    },

    importTools(sourceId, toolNames) {
      return apiFetch('/tool-sources/' + encodeURIComponent(sourceId) + '/import', {
        method: 'POST',
        body: { toolNames },
      });
    },

    syncToolSource(sourceId) {
      return apiFetch('/tool-sources/' + encodeURIComponent(sourceId) + '/sync', {
        method: 'POST',
      });
    },

    importSkill(content) {
      return apiFetch('/skills/import', { method: 'POST', body: { content } });
    },

    previewSkill(content) {
      return apiFetch('/skills/preview', { method: 'POST', body: { content } });
    },

    importSkillPackage(file) {
      const formData = new FormData();
      formData.append('file', file);
      return apiFetchRaw('/skills/import-package', { method: 'POST', body: formData });
    },

    previewSkillPackage(file) {
      const formData = new FormData();
      formData.append('file', file);
      return apiFetchRaw('/skills/preview-package', { method: 'POST', body: formData });
    },

    exportSkill(id) {
      return apiFetch('/skills/export/' + encodeURIComponent(id));
    },

    exportAllSkills(type) {
      const params = new URLSearchParams({ type });
      return apiFetch('/skills/export?' + params.toString());
    },
  };


  // ═══════════════════════════════════════════════════════
  // Utilities
  // ═══════════════════════════════════════════════════════

  function debounce(fn, ms) {
    let timer;
    return function (...args) {
      clearTimeout(timer);
      timer = setTimeout(() => fn.apply(this, args), ms);
    };
  }

  function t(key, params = {}) {
    const dict = I18N[state.locale] || I18N.en;
    const fallback = I18N.en[key] || key;
    const template = dict[key] || fallback;
    return template.replace(/\{(\w+)\}/g, (_, token) => String(params[token] ?? ''));
  }

  function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
  }

  function setPlaceholder(id, value) {
    const el = document.getElementById(id);
    if (el) el.placeholder = value;
  }

  function applyLocale() {
    document.documentElement.lang = state.locale === 'zh' ? 'zh-CN' : 'en';
    document.title = t('pageTitle');

    setText('header-title', t('headerTitle'));
    setText('tenant-filter-label', t('tenant'));
    setText('include-global-filter-text', t('includeGlobal'));
    setText('btn-import-skill-text', t('importSkill'));
    setText('btn-create-text', t('newExperience'));
    setText('tab-all-text', t('tabAll'));
    setText('tab-react-text', t('tabReact'));
    setText('tab-common-text', t('tabCommon'));
    setText('tab-tool-text', t('tabTool'));
    setText('tab-sources-text', t('tabSources'));
    setText('btn-export-all-text', t('exportAll'));
    setText('tool-sources-section-title', t('tabSources'));

    setText('form-label-type', t('type'));
    setText('form-type-option-empty', t('selectType'));
    setText('form-label-name', t('name'));
    setText('form-label-description', t('description'));
    setText('form-label-content', t('content'));
    setText('form-label-tags', t('tags'));
    setText('form-hint-comma-separated', t('commaSeparated'));
    setText('form-label-tenant-ids', t('tenantIds'));
    setText('form-hint-tenant', t('tenantHintMulti'));
    setPlaceholder('form-tenant-search', t('tenantSearchPlaceholder'));
    setText('form-label-disclosure-strategy', t('disclosureStrategy'));
    setText('form-disclosure-option-default', t('defaultNone'));
    setText('form-label-tool-invocation-path', t('toolInvocationPath'));
    setText('form-label-associated-tools', t('associatedTools'));
    setText('form-hint-comma-separated-2', t('commaSeparated'));
    setText('form-label-related-experiences', t('relatedExperiences'));
    setText('form-hint-related-experiences', t('relatedExperiencesHint'));
    setText('form-label-fast-intent', t('fastIntent'));
    setText('form-label-fastintent-enabled', t('enabled'));
    setText('form-fastintent-enabled-false', t('disabled'));
    setText('form-fastintent-enabled-true', t('enabled'));
    setText('form-label-fastintent-priority', t('priority'));
    setText('form-label-fastintent-mode', t('mode'));
    setText('form-label-fastintent-fallback', t('fallback'));
    setText('form-label-fastintent-match', t('fastIntentMatchJson'));
    setText('form-label-artifact-json', t('artifactJson'));
    setText('form-label-references', t('referencesLabel'));
    setText('form-label-assets', t('assetsLabel'));
    setText('form-label-properties-json', t('propertiesJson'));

    setText('modal-cancel', t('cancel'));
    setText('modal-save-text', t('save'));
    setText('skill-import-title', t('importSkillTitle'));
    setText('skill-import-content-label', t('pasteSkillMarkdown'));
    setText('skill-preview-title', t('preview'));
    setText('skill-import-cancel', t('cancel'));
    setText('btn-skill-preview-text', t('preview'));
    setText('btn-skill-import-submit-text', t('import'));
    setText('tab-upload-file-text', t('uploadPackage'));
    setText('tab-paste-text-text', t('pasteMarkdown'));
    setText('upload-dropzone-text', t('uploadDropzoneText'));
    setText('upload-dropzone-hint', t('uploadDropzoneHint'));
    setText('skill-import-result-title', t('importResultTitle'));
    setText('skill-export-title', t('exportSkillTitle'));
    setText('skill-export-close', t('close'));
    setText('btn-copy-skill-text', t('copyToClipboard'));
    setText('confirm-title', t('confirmDeleteTitle'));
    setText('confirm-cancel', t('cancel'));
    setText('confirm-ok', t('delete'));

    setPlaceholder('search-input', t('searchPlaceholder'));
    setPlaceholder('form-name', t('newExperience'));
    setPlaceholder('form-description', t('description'));
    setPlaceholder('form-content', t('content'));
    setPlaceholder('skill-import-content', '# SKILL: My Experience\n\n## Description\n...');

    setText('btn-prev-text', t('prev'));
    setText('btn-next-text', t('next'));
    dom.btnLanguageToggleText.textContent = state.locale === 'zh' ? 'EN' : '中文';

    if (state.activeTab === 'SOURCES') {
      dom.searchInput.placeholder = t('searchPlaceholderDisabled');
    }

    renderTenantFilter();
    renderFormTenantOptions();
    renderPagination();
  }

  function formatDate(instant) {
    if (!instant) return '';
    try {
      const d = new Date(instant);
      const now = new Date();
      const diff = now - d;
      if (diff < 60000) return t('justNow');
      if (diff < 3600000) return t('minutesAgo', { value: Math.floor(diff / 60000) });
      if (diff < 86400000) return t('hoursAgo', { value: Math.floor(diff / 3600000) });
      if (diff < 604800000) return t('daysAgo', { value: Math.floor(diff / 86400000) });
      const locale = state.locale === 'zh' ? 'zh-CN' : 'en-US';
      return d.toLocaleDateString(locale, {
        month: 'short',
        day: 'numeric',
        year: d.getFullYear() !== now.getFullYear() ? 'numeric' : undefined,
      });
    } catch (_) {
      return '';
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  function parseCommaSeparated(value) {
    return (value || '')
      .split(',')
      .map(item => item.trim())
      .filter(Boolean);
  }

  function stringifyJson(value) {
    return value ? JSON.stringify(value, null, 2) : '';
  }

  function parseJsonField(value, fieldLabel) {
    const trimmed = (value || '').trim();
    if (!trimmed) return null;
    try {
      return JSON.parse(trimmed);
    } catch (_) {
      throw new Error(t('toastJsonInvalid', { fieldLabel }));
    }
  }

  function getTypeBadgeClass(type) {
    switch (type) {
      case 'REACT':  return 'type-badge--react';
      case 'COMMON': return 'type-badge--common';
      case 'TOOL':   return 'type-badge--tool';
      default:       return '';
    }
  }

  // SVG icon helpers
  const icons = {
    edit: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>',
    trash: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>',
    download: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>',
    checkCircle: '<svg class="toast-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>',
    alertCircle: '<svg class="toast-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>',
    info: '<svg class="toast-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>',
    emptyBox: '<svg class="empty-state-icon" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>',
    chevronRight: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>',
    chevronDown: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>',
    wrench: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>',
  };


  // ═══════════════════════════════════════════════════════
  // Toast Notifications
  // ═══════════════════════════════════════════════════════

  function showToast(message, type = 'info') {
    const iconMap = { success: icons.checkCircle, error: icons.alertCircle, info: icons.info };
    const el = document.createElement('div');
    el.className = `toast toast--${type}`;
    el.innerHTML = `${iconMap[type] || iconMap.info}<span>${escapeHtml(message)}</span>`;
    dom.toastContainer.appendChild(el);

    setTimeout(() => {
      el.classList.add('toast-out');
      el.addEventListener('animationend', () => el.remove());
    }, 3000);
  }


  // ═══════════════════════════════════════════════════════
  // Rendering — Loading Skeletons
  // ═══════════════════════════════════════════════════════

  function renderLoading() {
    let html = '';
    for (let i = 0; i < 4; i++) {
      html += `
        <div class="skeleton-card">
          <div class="skeleton-line skeleton-line--title"></div>
          <div class="skeleton-line skeleton-line--text"></div>
          <div class="skeleton-line skeleton-line--text-short"></div>
          <div class="skeleton-line skeleton-line--tags"></div>
        </div>`;
    }
    dom.experienceList.innerHTML = html;
    dom.pagination.hidden = true;
  }


  // ═══════════════════════════════════════════════════════
  // Rendering — Empty State
  // ═══════════════════════════════════════════════════════

  function renderEmptyState() {
    const msg = state.isSearchMode
      ? t('emptySearchResult', { query: state.searchQuery })
      : (state.activeTab === 'ALL'
        ? t('emptyAnyResult')
        : t('emptyTypeResult', { type: state.activeTab }));
    const sub = state.isSearchMode ? t('emptySearchHint') : t('emptyCreateHint');

    dom.experienceList.innerHTML = `
      <div class="empty-state">
        ${icons.emptyBox}
        <h3 class="empty-state-title">${escapeHtml(msg)}</h3>
        <p class="empty-state-text">${escapeHtml(sub)}</p>
      </div>`;
    dom.pagination.hidden = true;
  }


  // ═══════════════════════════════════════════════════════
  // Rendering — Experience Cards
  // ═══════════════════════════════════════════════════════

  function renderExperienceCard(exp) {
    const tags = (exp.tags || []).map(t => `<span class="tag-chip">${escapeHtml(t)}</span>`).join('');
    const tenantTags = (exp.tenantIdList && exp.tenantIdList.length)
      ? exp.tenantIdList.map(tenantId => tenantId === GLOBAL_TENANT_ID
        ? `<span class="tag-chip">${escapeHtml(t('globalTag'))}</span>`
        : `<span class="tag-chip">${escapeHtml(t('tenantTag', { tenantId }))}</span>`).join('')
      : `<span class="tag-chip">${escapeHtml(t('globalTag'))}</span>`;
    const desc = exp.description || '';
    const dateStr = formatDate(exp.updatedAt || exp.createdAt);
    const summaryTags = [];
    if (exp.disclosureStrategy) {
      summaryTags.push(`<span class="tag-chip">${escapeHtml(exp.disclosureStrategy)}</span>`);
    }
    if (exp.fastIntentConfig && exp.fastIntentConfig.enabled) {
      summaryTags.push(`<span class="tag-chip">${escapeHtml(t('fastTag', { priority: String(exp.fastIntentConfig.priority ?? 0) }))}</span>`);
    }
    if (exp.associatedTools && exp.associatedTools.length) {
      summaryTags.push(`<span class="tag-chip">${escapeHtml(t('toolsTag', { count: String(exp.associatedTools.length) }))}</span>`);
    }
    if (exp.type === 'TOOL' && exp.toolInvocationPath) {
      summaryTags.push(`<span class="tag-chip">${escapeHtml(exp.toolInvocationPath)}</span>`);
    }
    const footerTags = [tenantTags, tags, summaryTags.join('')].filter(Boolean).join('');

    return `
      <article class="experience-card" data-id="${escapeHtml(exp.id)}">
        <div class="card-header">
          <div class="card-title-area">
            <span class="type-badge ${getTypeBadgeClass(exp.type)}">${escapeHtml(exp.type)}</span>
            <h3 class="card-name">${escapeHtml(exp.name)}</h3>
          </div>
          <div class="card-actions">
            <button class="btn-icon" data-action="edit" data-id="${escapeHtml(exp.id)}" title="${escapeHtml(t('editExperienceModal'))}">
              ${icons.edit}
            </button>
            <button class="btn-icon" data-action="export" data-id="${escapeHtml(exp.id)}" title="${escapeHtml(t('exportSkillTitle'))}">
              ${icons.download}
            </button>
            <button class="btn-icon" data-action="delete" data-id="${escapeHtml(exp.id)}" data-name="${escapeHtml(exp.name)}" data-source-info="${escapeHtml(exp.sourceInfo || '')}" title="${escapeHtml(t('delete'))}" style="color:var(--danger)">
              ${icons.trash}
            </button>
          </div>
        </div>
        ${desc ? `<p class="card-description">${escapeHtml(desc)}</p>` : ''}
        <div class="card-footer">
          ${footerTags ? `<div class="tag-list">${footerTags}</div>` : ''}
          ${dateStr ? `<span class="card-meta">${dateStr}</span>` : ''}
        </div>
      </article>`;
  }

  function renderExperienceList() {
    if (state.experiences.length === 0) {
      renderEmptyState();
      return;
    }
    dom.experienceList.innerHTML = state.experiences.map(renderExperienceCard).join('');
    renderPagination();
  }


  // ═══════════════════════════════════════════════════════
  // Rendering — Pagination
  // ═══════════════════════════════════════════════════════

  function renderPagination() {
    if (state.isSearchMode || state.pagination.total <= state.pagination.size) {
      dom.pagination.hidden = true;
      return;
    }
    const totalPages = Math.ceil(state.pagination.total / state.pagination.size);
    dom.pagination.hidden = false;
    dom.pageInfo.textContent = t('pageOf', { page: state.pagination.page, totalPages });
    dom.btnPrev.disabled = state.pagination.page <= 1;
    dom.btnNext.disabled = state.pagination.page >= totalPages;
  }


  // ═══════════════════════════════════════════════════════
  // Rendering — Tabs
  // ═══════════════════════════════════════════════════════

  function renderTabs() {
    const total = (state.stats.REACT || 0) + (state.stats.COMMON || 0) + (state.stats.TOOL || 0);
    dom.countAll.textContent = total;
    dom.countReact.textContent = state.stats.REACT || 0;
    dom.countCommon.textContent = state.stats.COMMON || 0;
    dom.countTool.textContent = state.stats.TOOL || 0;

    $$('.tab').forEach(tab => {
      tab.classList.toggle('active', tab.dataset.tab === state.activeTab);
    });
  }


  // ═══════════════════════════════════════════════════════
  // Rendering — Tool Sources
  // ═══════════════════════════════════════════════════════

  function renderToolSources() {
    if (state.activeTab !== 'SOURCES') {
      dom.toolSources.hidden = true;
      return;
    }
    dom.toolSources.hidden = false;

    if (state.toolSources.length === 0) {
      dom.toolSourcesList.innerHTML = `
        <div class="empty-state" style="padding:32px">
          ${icons.wrench}
          <h3 class="empty-state-title">${escapeHtml(t('noToolSources'))}</h3>
          <p class="empty-state-text">${escapeHtml(t('noToolSourcesConfigured'))}</p>
        </div>`;
      return;
    }

    dom.toolSourcesList.innerHTML = state.toolSources.map(src => {
      const isExpanded = state.expandedSource === src.id;
      const tools = state.sourceTools[src.id] || [];

      let toolsHtml = '';
      if (isExpanded) {
        if (tools.length === 0) {
          toolsHtml = `<p style="padding:8px 0;color:var(--text-muted);font-size:0.8125rem;">${escapeHtml(t('noToolsAvailable'))}</p>`;
        } else {
          toolsHtml = `
            <div class="tool-list-toolbar">
              <span class="tool-list-selectable-count" data-source="${escapeHtml(src.id)}"></span>
              <div class="tool-list-toolbar-actions">
                <button class="btn btn-ghost btn-xs" data-action="select-all" data-source="${escapeHtml(src.id)}">${escapeHtml(t('selectAll'))}</button>
                <button class="btn btn-ghost btn-xs" data-action="deselect-all" data-source="${escapeHtml(src.id)}">${escapeHtml(t('deselectAll'))}</button>
              </div>
            </div>
            <div class="tool-list">
              ${tools.map(tool => `
                <label class="tool-item">
                  <input type="checkbox" value="${escapeHtml(tool.name)}" ${tool.imported ? 'disabled checked' : ''}>
                  <div class="tool-item-info">
                    <div class="tool-item-name">${escapeHtml(tool.name)}</div>
                    ${tool.description ? `<div class="tool-item-desc">${escapeHtml(tool.description)}</div>` : ''}
                  </div>
                  ${tool.imported ? `<span class="tool-imported-badge">${escapeHtml(t('imported'))}</span>` : ''}
                </label>
              `).join('')}
            </div>
            <div class="tool-source-footer">
              <button class="btn btn-ghost btn-sm" data-action="sync-source" data-source="${escapeHtml(src.id)}">
                ${escapeHtml(t('syncSource'))}
              </button>
              <button class="btn btn-primary btn-sm" data-action="import-tools" data-source="${escapeHtml(src.id)}" disabled>
                ${escapeHtml(t('importSelected', { count: 0 }))}
              </button>
            </div>`;
        }
      }

      return `
        <div class="tool-source-card ${isExpanded ? 'expanded' : ''}" data-source-id="${escapeHtml(src.id)}">
          <div class="tool-source-header" data-action="toggle-source" data-source="${escapeHtml(src.id)}">
            <div class="tool-source-info">
              <div class="tool-source-name">${escapeHtml(src.name)}</div>
              <div class="tool-source-meta">
                ${escapeHtml(t('importedTotal', { imported: src.importedCount || 0, total: src.totalCount || 0 }))}
              </div>
            </div>
            ${isExpanded ? icons.chevronDown : icons.chevronRight}
          </div>
          <div class="tool-source-body">
            ${toolsHtml}
          </div>
        </div>`;
    }).join('');

    // Initialise toolbar counts for any expanded cards
    dom.toolSourcesList.querySelectorAll('.tool-source-card.expanded').forEach(card => updateImportButton(card));
  }


  // ═══════════════════════════════════════════════════════
  // Data Loading
  // ═══════════════════════════════════════════════════════

  async function loadStats() {
    try {
      const stats = await api.getStats();
      state.stats = stats || { REACT: 0, COMMON: 0, TOOL: 0 };
      renderTabs();
    } catch (err) {
      console.warn('Failed to load stats:', err);
    }
  }

  function normalizeTenantOptions(payload) {
    if (!payload) return [];
    const raw = Array.isArray(payload) ? payload : (payload.data || []);
    return raw
      .map(item => {
        if (typeof item === 'string') {
          return { id: item, name: item };
        }
        if (item && typeof item === 'object') {
          const id = item.id || item.tenantId || item.code || item.value;
          if (!id) return null;
          return { id: String(id), name: String(item.name || item.label || id) };
        }
        return null;
      })
      .filter(Boolean);
  }

  function renderTenantFilter() {
    if (!dom.tenantFilter) return;
    const options = [
      `<option value="">${escapeHtml(t('allTenants'))}</option>`,
      ...state.tenantOptions.map(opt => `<option value="${escapeHtml(opt.id)}">${escapeHtml(opt.name)}</option>`),
    ];
    dom.tenantFilter.innerHTML = options.join('');
    dom.tenantFilter.value = state.selectedTenant || '';
    updateIncludeGlobalFilterState();
  }

  function shouldIncludeGlobalForRequests() {
    if (!state.selectedTenant || state.selectedTenant === GLOBAL_TENANT_ID) {
      return true;
    }
    return !!state.includeGlobal;
  }

  function updateIncludeGlobalFilterState() {
    if (!dom.includeGlobalFilter) return;
    const disabled = !state.selectedTenant || state.selectedTenant === GLOBAL_TENANT_ID;
    dom.includeGlobalFilter.disabled = disabled;
    dom.includeGlobalFilter.checked = disabled ? true : !!state.includeGlobal;
  }

  function renderFormTenantOptions() {
    if (!dom.formTenantIds) return;
    state.formTenantSelections = toTenantViewSelectionList(state.formTenantSelections);
    const selectedValues = new Set(state.formTenantSelections);
    const allOptions = [{ id: GLOBAL_TENANT_ID, name: t('globalTag') }];
    state.tenantOptions.forEach(option => {
      if (option.id !== GLOBAL_TENANT_ID && !allOptions.some(existing => existing.id === option.id)) {
        allOptions.push(option);
      }
    });
    selectedValues.forEach(value => {
      if (!allOptions.some(option => option.id === value)) {
        allOptions.push({ id: value, name: value === GLOBAL_TENANT_ID ? t('globalTag') : value });
      }
    });
    dom.formTenantIds.innerHTML = allOptions
      .map(option => `<option value="${escapeHtml(option.id)}" ${selectedValues.has(option.id) ? 'selected' : ''}>${escapeHtml(option.name)}</option>`)
      .join('');

    const labelById = new Map(allOptions.map(option => [option.id, option.name]));
    const selectedTagsHtml = (state.formTenantSelections || [])
      .map(value => {
        const name = labelById.get(value) || value;
        const removeButton = value === GLOBAL_TENANT_ID
          ? ''
          : `<button type="button" class="tenant-multiselect-tag-remove" data-remove-tenant="${escapeHtml(value)}">x</button>`;
        return `<span class="tenant-multiselect-tag">${escapeHtml(name)}${removeButton}</span>`;
      })
      .join('');
    dom.formTenantTags.innerHTML = selectedTagsHtml;
    dom.formTenantPlaceholder.hidden = (state.formTenantSelections || []).length > 0;
    dom.formTenantPlaceholder.textContent = t('tenantSelectPlaceholder');

    if (!dom.formTenantOptions) return;
    if (allOptions.length === 0) {
      dom.formTenantOptions.innerHTML = `<div class="tenant-multiselect-empty">${escapeHtml(t('tenantNoOptions'))}</div>`;
      return;
    }
    const tenantSearchQuery = (dom.formTenantSearch?.value || '').trim().toLowerCase();
    const filteredOptions = tenantSearchQuery
      ? allOptions.filter(option => {
          const id = (option.id || '').toLowerCase();
          const name = (option.name || '').toLowerCase();
          return id.includes(tenantSearchQuery) || name.includes(tenantSearchQuery);
        })
      : allOptions;
    if (filteredOptions.length === 0) {
      dom.formTenantOptions.innerHTML = `<div class="tenant-multiselect-empty">${escapeHtml(t('tenantNoMatches'))}</div>`;
      return;
    }
    dom.formTenantOptions.innerHTML = filteredOptions
      .map(option => `
        <button type="button" class="tenant-multiselect-option" data-tenant-option="${escapeHtml(option.id)}">
          <input type="checkbox" ${selectedValues.has(option.id) ? 'checked' : ''} tabindex="-1">
          <span>${escapeHtml(option.name)}</span>
        </button>
      `)
      .join('');
  }

  function normalizeTenantSelectionList(values) {
    const seen = new Set();
    const normalized = (values || [])
      .map(value => {
        const normalizedValue = (value || '').trim();
        return normalizedValue.toLowerCase() === GLOBAL_TENANT_ID ? GLOBAL_TENANT_ID : normalizedValue;
      })
      .filter(value => {
        if (!value || seen.has(value)) return false;
        seen.add(value);
        return true;
      });
    return normalized.includes(GLOBAL_TENANT_ID) ? [GLOBAL_TENANT_ID] : normalized;
  }

  function toTenantViewSelectionList(values) {
    const normalized = normalizeTenantSelectionList(values);
    return normalized.length ? normalized : [GLOBAL_TENANT_ID];
  }

  function serializeTenantSelectionList(values) {
    const normalized = normalizeTenantSelectionList(values);
    if (!normalized.length || normalized.includes(GLOBAL_TENANT_ID)) {
      return [];
    }
    return normalized;
  }

  function resetFormTenantSearch() {
    if (dom.formTenantSearch) {
      dom.formTenantSearch.value = '';
    }
  }

  function toggleFormTenantDropdown(open) {
    if (!dom.formTenantDropdown || !dom.formTenantTrigger) return;
    const shouldOpen = typeof open === 'boolean' ? open : dom.formTenantDropdown.hidden;
    dom.formTenantDropdown.hidden = !shouldOpen;
    dom.formTenantTrigger.setAttribute('aria-expanded', shouldOpen ? 'true' : 'false');
    if (shouldOpen && dom.formTenantSearch) {
      setTimeout(() => dom.formTenantSearch.focus(), 0);
    }
  }

  function toggleTenantSelection(tenantId) {
    if (!tenantId) return;
    if (tenantId === GLOBAL_TENANT_ID) {
      state.formTenantSelections = [GLOBAL_TENANT_ID];
      renderFormTenantOptions();
      return;
    }
    const selected = toTenantViewSelectionList(state.formTenantSelections)
      .filter(value => value !== GLOBAL_TENANT_ID);
    state.formTenantSelections = selected.includes(tenantId)
      ? selected.filter(value => value !== tenantId)
      : [...selected, tenantId];
    renderFormTenantOptions();
  }

  async function loadTenants() {
    try {
      const tenants = normalizeTenantOptions(await api.listTenants());
      state.tenantOptions = tenants;
    } catch (err) {
      state.tenantOptions = [];
      console.warn('Failed to load tenants:', err);
    }
    renderTenantFilter();
    renderFormTenantOptions();
  }

  async function loadExperiences() {
    state.loading = true;
    renderLoading();

    try {
      if (state.isSearchMode && state.searchQuery) {
        const result = await api.searchExperiences(
          state.searchQuery,
          state.activeTab,
          state.pagination.size
        );
        state.experiences = result.data || result || [];
        state.pagination.total = state.experiences.length;
      } else {
        const result = await api.listExperiences(
          state.activeTab,
          state.pagination.page,
          state.pagination.size
        );
        state.experiences = result.data || [];
        state.pagination.total = result.total || 0;
        state.pagination.page = result.page || 1;
      }
      renderExperienceList();
    } catch (err) {
      showToast(t('toastLoadFailed', { message: err.message }), 'error');
      renderEmptyState();
    } finally {
      state.loading = false;
    }
  }

  async function loadToolSources() {
    try {
      const sources = await api.listToolSources();
      state.toolSources = sources || [];
      renderToolSources();
    } catch (err) {
      console.warn('Failed to load tool sources:', err);
      state.toolSources = [];
      renderToolSources();
    }
  }


  // ═══════════════════════════════════════════════════════
  // Tab Switching
  // ═══════════════════════════════════════════════════════

  async function switchTab(tab) {
    state.activeTab = tab;
    state.pagination.page = 1;
    state.searchQuery = '';
    state.isSearchMode = false;
    dom.searchInput.value = '';
    renderTabs();

    if (tab === 'SOURCES') {
      dom.experienceList.hidden = true;
      dom.pagination.hidden = true;
      dom.searchInput.disabled = true;
      dom.searchInput.placeholder = t('searchPlaceholderDisabled');
      dom.tenantFilterWrap.hidden = true;
      await loadToolSources();
    } else {
      dom.experienceList.hidden = false;
      dom.searchInput.disabled = false;
      dom.searchInput.placeholder = t('searchPlaceholder');
      dom.tenantFilterWrap.hidden = false;
      dom.toolSources.hidden = true;
      await loadExperiences();
    }
  }


  // ═══════════════════════════════════════════════════════
  // Search
  // ═══════════════════════════════════════════════════════

  const handleSearch = debounce(async () => {
    const q = dom.searchInput.value.trim();
    if (q === state.searchQuery) return;

    state.searchQuery = q;
    state.isSearchMode = q.length > 0;
    state.pagination.page = 1;

    await loadExperiences();
  }, 300);


  // ═══════════════════════════════════════════════════════
  // Create / Edit Modal
  // ═══════════════════════════════════════════════════════

  function openModal(title, isEdit = false) {
    dom.modalTitle.textContent = title;
    dom.formGroupType.style.display = isEdit ? 'none' : '';
    dom.editModal.hidden = false;
    updateAdvancedSections();

    // Focus first visible input after transition
    setTimeout(() => {
      const firstInput = isEdit ? dom.formName : dom.formType;
      firstInput.focus();
    }, 100);
  }

  function closeModal() {
    dom.editModal.hidden = true;
    toggleFormTenantDropdown(false);
    resetFormTenantSearch();
    dom.formId.value = '';
    dom.formType.value = '';
    dom.formName.value = '';
    dom.formDescription.value = '';
    dom.formContent.value = '';
    dom.formTags.value = '';
    state.formTenantSelections = [GLOBAL_TENANT_ID];
    renderFormTenantOptions();
    dom.formDisclosureStrategy.value = '';
    dom.formToolInvocationPath.value = TOOL_INVOCATION_PATH_DEFAULT;
    dom.formAssociatedTools.value = '';
    dom.formRelatedExperiences.value = '';
    dom.formFastIntentEnabled.value = 'false';
    dom.formFastIntentPriority.value = '0';
    dom.formFastIntentMode.value = 'FASTPATH';
    dom.formFastIntentFallback.value = 'REFERENCE_ONLY';
    dom.formFastIntentMatch.value = '';
    dom.formArtifactJson.value = '';
    dom.formPropertiesJson.value = '';
    state.editingId = null;
  }

  function updateAdvancedSections() {
    const currentType = dom.formType.value;
    const isReact = currentType === 'REACT';
    const isCommon = currentType === 'COMMON';
    const isTool = currentType === 'TOOL';
    dom.formFastIntentSection.hidden = !isReact;
    dom.formGroupArtifactJson.hidden = isCommon;
    dom.formGroupAssociatedTools.hidden = isCommon;
    dom.formGroupRelatedExperiences.hidden = isCommon;
    dom.formGroupToolInvocationPath.hidden = !isTool;
    if (isTool && !dom.formToolInvocationPath.value) {
      dom.formToolInvocationPath.value = TOOL_INVOCATION_PATH_DEFAULT;
    }
  }

  function openCreateForm() {
    state.editingId = null;
    openModal(t('newExperienceModal'), false);
    toggleFormTenantDropdown(false);

    // Pre-select type if tab is specific
    if (state.activeTab === 'REACT' || state.activeTab === 'COMMON' || state.activeTab === 'TOOL') {
      dom.formType.value = state.activeTab;
      updateAdvancedSections();
    }
    state.formTenantSelections = [GLOBAL_TENANT_ID];
    resetFormTenantSearch();
    renderFormTenantOptions();
    dom.formToolInvocationPath.value = TOOL_INVOCATION_PATH_DEFAULT;
  }

  async function openEditModal(id) {
    try {
      const exp = await api.getExperience(id);
      state.editingId = id;
      toggleFormTenantDropdown(false);

      dom.formId.value = exp.id;
      dom.formType.value = exp.type;
      dom.formName.value = exp.name || '';
      dom.formDescription.value = exp.description || '';
      dom.formContent.value = exp.content || '';
      dom.formTags.value = (exp.tags || []).join(', ');
      state.formTenantSelections = toTenantViewSelectionList(exp.tenantIdList);
      resetFormTenantSearch();
      renderFormTenantOptions();
      dom.formDisclosureStrategy.value = exp.disclosureStrategy || '';
      dom.formToolInvocationPath.value = resolveToolInvocationPath(exp);
      dom.formAssociatedTools.value = (exp.associatedTools || []).join(', ');
      dom.formRelatedExperiences.value = (exp.relatedExperiences || []).join(', ');
      dom.formFastIntentEnabled.value = exp.fastIntentConfig && exp.fastIntentConfig.enabled ? 'true' : 'false';
      dom.formFastIntentPriority.value = exp.fastIntentConfig ? (exp.fastIntentConfig.priority ?? 0) : 0;
      dom.formFastIntentMode.value = exp.fastIntentConfig && exp.fastIntentConfig.onMatch
        ? (exp.fastIntentConfig.onMatch.mode || 'FASTPATH')
        : 'FASTPATH';
      dom.formFastIntentFallback.value = exp.fastIntentConfig && exp.fastIntentConfig.onMatch
        ? (exp.fastIntentConfig.onMatch.fallback || 'REFERENCE_ONLY')
        : 'REFERENCE_ONLY';
      dom.formFastIntentMatch.value = stringifyJson(exp.fastIntentConfig ? exp.fastIntentConfig.match : null);
      dom.formArtifactJson.value = stringifyJson(exp.artifact);
      dom.formPropertiesJson.value = stringifyJson(exp.properties);
      renderReferencesAndAssets(exp);
      wireResummarizeButton(exp.id);

      openModal(t('editExperienceModal'), true);
    } catch (err) {
      showToast(t('toastLoadExperienceFailed', { message: err.message }), 'error');
    }
  }

  async function submitForm() {
    const name = dom.formName.value.trim();
    const content = dom.formContent.value.trim();

    if (!name) {
      showToast(t('toastNameRequired'), 'error');
      dom.formName.focus();
      return;
    }
    if (!content) {
      showToast(t('toastContentRequired'), 'error');
      dom.formContent.focus();
      return;
    }

    const tags = dom.formTags.value
      .split(',')
      .map(t => t.trim())
      .filter(Boolean);

    let artifact;
    let properties;
    let fastIntentMatch;
    try {
      artifact = parseJsonField(dom.formArtifactJson.value, t('fieldArtifactJson'));
      properties = parseJsonField(dom.formPropertiesJson.value, t('fieldPropertiesJson'));
      fastIntentMatch = parseJsonField(dom.formFastIntentMatch.value, t('fieldFastIntentMatchJson'));
    } catch (err) {
      showToast(err.message, 'error');
      return;
    }

    const fastIntentEnabled = dom.formFastIntentEnabled.value === 'true';
    const fastIntentPriority = parseInt(dom.formFastIntentPriority.value || '0', 10) || 0;
    const hasFastIntentFields = fastIntentEnabled
      || fastIntentPriority !== 0
      || !!(dom.formFastIntentMatch.value || '').trim();
    const payload = {
      name,
      description: dom.formDescription.value.trim(),
      content,
      tags,
      tenantIdList: serializeTenantSelectionList(state.formTenantSelections),
      disclosureStrategy: dom.formDisclosureStrategy.value || null,
      associatedTools: parseCommaSeparated(dom.formAssociatedTools.value),
      relatedExperiences: parseCommaSeparated(dom.formRelatedExperiences.value),
      artifact,
      fastIntentConfig: hasFastIntentFields
        ? {
            enabled: fastIntentEnabled,
            priority: fastIntentPriority,
            match: fastIntentMatch,
            onMatch: {
              mode: dom.formFastIntentMode.value || 'FASTPATH',
              fallback: dom.formFastIntentFallback.value || 'REFERENCE_ONLY',
            },
          }
        : null,
      properties: properties || {},
    };

    try {
      const currentType = dom.formType.value;
      if (currentType === 'TOOL') {
        payload.properties[TOOL_INVOCATION_PATH_PROPERTY] = dom.formToolInvocationPath.value || TOOL_INVOCATION_PATH_DEFAULT;
      } else {
        delete payload.properties[TOOL_INVOCATION_PATH_PROPERTY];
      }
      if (currentType === 'COMMON') {
        payload.artifact = null;
        payload.associatedTools = [];
        payload.relatedExperiences = [];
      }
      if (currentType !== 'REACT') {
        payload.fastIntentConfig = null;
      }

      if (state.editingId) {
        await api.updateExperience(state.editingId, payload);
        showToast(t('toastUpdated'), 'success');
      } else {
        const type = dom.formType.value;
        if (!type) {
          showToast(t('toastTypeRequired'), 'error');
          dom.formType.focus();
          return;
        }
        await api.createExperience({
          ...payload,
          type,
        });
        showToast(t('toastCreated'), 'success');
      }
      closeModal();
      await Promise.all([loadStats(), loadExperiences()]);
    } catch (err) {
      showToast(t('toastSaveFailed', { message: err.message }), 'error');
    }
  }

  function resolveToolInvocationPath(exp) {
    if (exp && exp.toolInvocationPath) {
      return exp.toolInvocationPath;
    }
    if (exp && exp.properties && typeof exp.properties[TOOL_INVOCATION_PATH_PROPERTY] === 'string' && exp.properties[TOOL_INVOCATION_PATH_PROPERTY]) {
      return exp.properties[TOOL_INVOCATION_PATH_PROPERTY];
    }
    return TOOL_INVOCATION_PATH_DEFAULT;
  }

  function wireResummarizeButton(experienceId) {
    const btn = document.getElementById('btn-resummarize');
    if (!btn || !experienceId) {
      return;
    }
    btn.textContent = t('resummarizeBtn');
    btn.onclick = async () => {
      btn.disabled = true;
      const original = btn.textContent;
      btn.textContent = '…';
      try {
        const resp = await fetch(`${API_BASE}/experiences/${encodeURIComponent(experienceId)}/resummarize`, {
          method: 'POST',
        });
        if (!resp.ok) {
          throw new Error('HTTP ' + resp.status);
        }
        const payload = await resp.json();
        const n = payload.updated ?? 0;
        showToast(t('resummarizeOk', { n }), 'success');
        // Reload experience to reflect new descriptions
        const detailResp = await fetch(`${API_BASE}/experiences/${encodeURIComponent(experienceId)}`);
        if (detailResp.ok) {
          const exp = await detailResp.json();
          renderReferencesAndAssets(exp);
        }
      } catch (err) {
        showToast(t('resummarizeFail') + ': ' + err.message, 'error');
      } finally {
        btn.disabled = false;
        btn.textContent = original;
      }
    };
  }

  function renderReferencesAndAssets(exp) {
    const refs = (exp && Array.isArray(exp.references)) ? exp.references : [];
    const assets = (exp && Array.isArray(exp.assets)) ? exp.assets : [];
    if (dom.formGroupReferences) {
      dom.formGroupReferences.hidden = refs.length === 0;
      if (refs.length > 0) {
        dom.formReferencesList.innerHTML = refs.map(r => {
          const size = r.size != null ? ` · ${r.size}B` : '';
          const desc = r.description ? ` — ${escapeHtml(r.description)}` : '';
          const preview = r.content ? `<details><summary style="cursor:pointer;color:#888;">content</summary><pre style="white-space:pre-wrap;margin:4px 0;">${escapeHtml(r.content)}</pre></details>` : '';
          return `<div style="padding:4px 0;border-bottom:1px solid #eee;"><strong>${escapeHtml(r.path || '')}</strong><span style="color:#888;">${size}</span>${desc}${preview}</div>`;
        }).join('');
      }
    }
    if (dom.formGroupAssets) {
      dom.formGroupAssets.hidden = assets.length === 0;
      if (assets.length > 0) {
        dom.formAssetsList.innerHTML = assets.map(a => {
          const role = a.role ? ` [${escapeHtml(a.role)}]` : '';
          const size = a.size != null ? ` · ${a.size}B` : '';
          const desc = a.description ? ` — ${escapeHtml(a.description)}` : '';
          const avail = a.contentAvailable
            ? ` <a href="#" data-asset-path="${escapeHtml(a.path || '')}" class="asset-fetch-link" style="color:#0969da;">[load content]</a>`
            : '';
          return `<div style="padding:4px 0;border-bottom:1px solid #eee;" data-asset-row="${escapeHtml(a.path || '')}"><strong>${escapeHtml(a.path || '')}</strong><span style="color:#888;">${role}${size}</span>${desc}${avail}</div>`;
        }).join('');
        dom.formAssetsList.querySelectorAll('.asset-fetch-link').forEach(link => {
          link.addEventListener('click', async (ev) => {
            ev.preventDefault();
            const path = link.getAttribute('data-asset-path');
            try {
              const encoded = path.split('/').map(encodeURIComponent).join('/');
              const resp = await fetch(`${API_BASE}/experiences/${encodeURIComponent(exp.id)}/assets/${encoded}`);
              if (!resp.ok) {
                throw new Error('HTTP ' + resp.status);
              }
              const payload = await resp.json();
              const row = dom.formAssetsList.querySelector(`[data-asset-row="${CSS.escape(path)}"]`);
              if (row) {
                const pre = document.createElement('pre');
                pre.style.whiteSpace = 'pre-wrap';
                pre.style.margin = '4px 0';
                pre.style.background = '#f6f8fa';
                pre.style.padding = '4px';
                const content = payload.content || '';
                pre.textContent = content.length > 2000 ? content.slice(0, 2000) + '…(truncated)' : content;
                row.appendChild(pre);
                link.remove();
              }
            } catch (err) {
              showToast('Load asset failed: ' + err.message, 'error');
            }
          });
        });
      }
    }
  }


  // ═══════════════════════════════════════════════════════
  // Delete
  // ═══════════════════════════════════════════════════════

  function confirmDelete(id, name, sourceInfo) {
    state.deleteTarget = { id, sourceInfo: sourceInfo || null };
    dom.confirmMessage.innerHTML = `${escapeHtml(t('deleteConfirm', { name }))}`;
    dom.confirmDialog.hidden = false;
  }

  function closeConfirm() {
    dom.confirmDialog.hidden = true;
    state.deleteTarget = null;
  }

  function invalidateSourceCache(sourceId) {
    if (!sourceId) return;
    delete state.sourceTools[sourceId];
    if (state.expandedSource === sourceId) {
      state.expandedSource = null;
    }
  }

  async function executeDelete() {
    if (!state.deleteTarget) return;
    const { id, sourceInfo } = state.deleteTarget;
    closeConfirm();

    try {
      await api.deleteExperience(id);
      showToast(t('toastDeleted'), 'success');
      invalidateSourceCache(sourceInfo);
      await Promise.all([
        loadStats(),
        loadExperiences(),
        state.activeTab === 'SOURCES' ? loadToolSources() : Promise.resolve(),
      ]);
    } catch (err) {
      showToast(t('toastDeleteFailed', { message: err.message }), 'error');
    }
  }


  // ═══════════════════════════════════════════════════════
  // SKILL Import
  // ═══════════════════════════════════════════════════════

  let selectedFile = null;
  let currentImportMode = 'upload'; // 'upload' | 'paste'

  function switchImportTab(mode) {
    currentImportMode = mode;
    dom.tabUploadFile.classList.toggle('active', mode === 'upload');
    dom.tabPasteText.classList.toggle('active', mode === 'paste');
    dom.importTabUpload.hidden = mode !== 'upload';
    dom.importTabPaste.hidden = mode !== 'paste';
    // Reset preview/result when switching tabs
    dom.skillPreviewArea.hidden = true;
    dom.skillPreviewContent.innerHTML = '';
    dom.skillImportResultArea.hidden = true;
    dom.skillImportResultContent.innerHTML = '';
  }

  function openSkillImport() {
    dom.skillImportContent.value = '';
    dom.skillPreviewArea.hidden = true;
    dom.skillPreviewContent.innerHTML = '';
    dom.skillImportResultArea.hidden = true;
    dom.skillImportResultContent.innerHTML = '';
    selectedFile = null;
    dom.uploadFileInfo.hidden = true;
    dom.uploadFileName.textContent = '';
    dom.skillUploadInput.value = '';
    switchImportTab('upload');
    dom.skillImportModal.hidden = false;
  }

  function closeSkillImport() {
    dom.skillImportModal.hidden = true;
    selectedFile = null;
  }

  function handleFileSelect(file) {
    if (!file) return;
    const name = file.name.toLowerCase();
    if (!name.endsWith('.tgz') && !name.endsWith('.tar.gz') && !name.endsWith('.zip')) {
      showToast(t('toastImportFailed', { message: 'Unsupported file type. Use .tgz or .zip' }), 'error');
      return;
    }
    selectedFile = file;
    dom.uploadFileName.textContent = file.name;
    dom.uploadFileInfo.hidden = false;
    // Reset preview when file changes
    dom.skillPreviewArea.hidden = true;
    dom.skillImportResultArea.hidden = true;
  }

  function clearFileSelection() {
    selectedFile = null;
    dom.uploadFileInfo.hidden = true;
    dom.uploadFileName.textContent = '';
    dom.skillUploadInput.value = '';
    dom.skillPreviewArea.hidden = true;
    dom.skillImportResultArea.hidden = true;
  }

  function renderImportResult(result) {
    let html = '';

    // React experience summary
    if (result.experience) {
      const exp = result.experience;
      dom.skillPreviewArea.hidden = false;
      dom.skillPreviewContent.innerHTML = `
        <div class="preview-field"><span class="preview-label">${escapeHtml(t('previewType'))}: </span><span class="preview-value">${escapeHtml(exp.type || '')}</span></div>
        <div class="preview-field"><span class="preview-label">${escapeHtml(t('previewName'))}: </span><span class="preview-value">${escapeHtml(exp.name || '')}</span></div>
        <div class="preview-field"><span class="preview-label">${escapeHtml(t('previewDescription'))}: </span><span class="preview-value">${escapeHtml(exp.description || t('notAvailable'))}</span></div>
        ${exp.tags && exp.tags.length ? `<div class="preview-field"><span class="preview-label">${escapeHtml(t('previewTags'))}: </span><span class="preview-value">${exp.tags.map(tag => escapeHtml(tag)).join(', ')}</span></div>` : ''}
      `;
    }

    // Tool experience (CLI binding)
    if (result.toolExperience) {
      const tool = result.toolExperience;
      html += `<div class="import-result-section"><div class="import-result-section-title">Tool Experience (CLI)</div>
        <div class="preview-field"><span class="preview-label">Name: </span><span class="preview-value">${escapeHtml(tool.name || '')}</span></div>
        <div class="preview-field"><span class="preview-label">ID: </span><span class="preview-value">${escapeHtml(tool.id || '')}</span></div>
        <div class="preview-field"><span class="preview-label">${escapeHtml(t('previewDescription'))}: </span><span class="preview-value">${escapeHtml(tool.description || '')}</span></div>
      </div>`;
    }

    // References
    if (result.references && result.references.length) {
      html += `<div class="import-result-section"><div class="import-result-section-title">${escapeHtml(t('referencesLabel'))} (${result.references.length})</div><ul class="import-result-list">`;
      for (const r of result.references) {
        const desc = r.description ? ` — ${escapeHtml(r.description)}` : '';
        const size = r.size != null ? ` · ${r.size}B` : '';
        html += `<li><strong>${escapeHtml(r.path || '')}</strong><span style="color:#888;">${size}</span>${desc}</li>`;
      }
      html += `</ul></div>`;
    }

    // Assets
    if (result.assets && result.assets.length) {
      html += `<div class="import-result-section"><div class="import-result-section-title">${escapeHtml(t('assetsLabel'))} (${result.assets.length})</div><ul class="import-result-list">`;
      for (const a of result.assets) {
        const role = a.role ? ` [${escapeHtml(a.role)}]` : '';
        const size = a.size != null ? ` · ${a.size}B` : '';
        const desc = a.description ? ` — ${escapeHtml(a.description)}` : '';
        html += `<li><strong>${escapeHtml(a.path || '')}</strong><span style="color:#888;">${role}${size}</span>${desc}</li>`;
      }
      html += `</ul></div>`;
    }

    // Processed files
    if (result.processedFiles && result.processedFiles.length) {
      html += `<div class="import-result-section"><div class="import-result-section-title">${escapeHtml(t('processedFiles'))}</div><ul class="import-result-list">`;
      for (const f of result.processedFiles) {
        html += `<li><span class="file-status file-status--ok">✓</span> ${escapeHtml(f)}</li>`;
      }
      html += `</ul></div>`;
    }

    // Skipped files
    if (result.skippedFiles && result.skippedFiles.length) {
      html += `<div class="import-result-section"><div class="import-result-section-title">${escapeHtml(t('skippedFiles'))}</div><ul class="import-result-list">`;
      for (const sf of result.skippedFiles) {
        html += `<li><span class="file-status file-status--skip">⊘</span> ${escapeHtml(sf.path)} <span class="skip-reason">— ${escapeHtml(sf.reason)}</span></li>`;
      }
      html += `</ul></div>`;
    }

    // Warnings
    if (result.warnings && result.warnings.length) {
      html += `<div class="import-result-section"><div class="import-result-section-title">${escapeHtml(t('warnings'))}</div><ul class="import-result-list">`;
      for (const w of result.warnings) {
        html += `<li><span class="file-status file-status--skip">⚠</span> ${escapeHtml(w)}</li>`;
      }
      html += `</ul></div>`;
    }

    if (html) {
      dom.skillImportResultArea.hidden = false;
      dom.skillImportResultContent.innerHTML = html;
    }
  }

  async function previewSkillImport() {
    if (currentImportMode === 'upload') {
      if (!selectedFile) {
        showToast(t('toastNoFileSelected'), 'error');
        return;
      }
      try {
        const result = await api.previewSkillPackage(selectedFile);
        renderImportResult(result);
      } catch (err) {
        showToast(t('toastPreviewFailed', { message: err.message }), 'error');
      }
    } else {
      const content = dom.skillImportContent.value.trim();
      if (!content) {
        showToast(t('toastPasteSkillFirst'), 'error');
        return;
      }
      try {
        const preview = await api.previewSkill(content);
        dom.skillPreviewArea.hidden = false;
        dom.skillPreviewContent.innerHTML = `
          <div class="preview-field"><span class="preview-label">${escapeHtml(t('previewType'))}: </span><span class="preview-value">${escapeHtml(preview.type)}</span></div>
          <div class="preview-field"><span class="preview-label">${escapeHtml(t('previewName'))}: </span><span class="preview-value">${escapeHtml(preview.name)}</span></div>
          <div class="preview-field"><span class="preview-label">${escapeHtml(t('previewDescription'))}: </span><span class="preview-value">${escapeHtml(preview.description || t('notAvailable'))}</span></div>
          ${preview.tags && preview.tags.length ? `<div class="preview-field"><span class="preview-label">${escapeHtml(t('previewTags'))}: </span><span class="preview-value">${preview.tags.map(tag => escapeHtml(tag)).join(', ')}</span></div>` : ''}
        `;
      } catch (err) {
        showToast(t('toastPreviewFailed', { message: err.message }), 'error');
      }
    }
  }

  function openImportForm(exp) {
    closeSkillImport();
    state.editingId = null;
    toggleFormTenantDropdown(false);

    dom.formId.value = '';
    dom.formType.value = exp.type || 'REACT';
    dom.formName.value = exp.name || '';
    dom.formDescription.value = exp.description || '';
    dom.formContent.value = exp.content || '';
    dom.formTags.value = (exp.tags || []).join(', ');
    state.formTenantSelections = [GLOBAL_TENANT_ID];
    resetFormTenantSearch();
    renderFormTenantOptions();
    dom.formDisclosureStrategy.value = exp.disclosureStrategy || '';
    dom.formToolInvocationPath.value = resolveToolInvocationPath(exp);
    dom.formAssociatedTools.value = (exp.associatedTools || []).join(', ');
    dom.formRelatedExperiences.value = (exp.relatedExperiences || []).join(', ');
    dom.formFastIntentEnabled.value = exp.fastIntentConfig && exp.fastIntentConfig.enabled ? 'true' : 'false';
    dom.formFastIntentPriority.value = exp.fastIntentConfig ? (exp.fastIntentConfig.priority ?? 0) : 0;
    dom.formFastIntentMode.value = exp.fastIntentConfig && exp.fastIntentConfig.onMatch
      ? (exp.fastIntentConfig.onMatch.mode || 'FASTPATH')
      : 'FASTPATH';
    dom.formFastIntentFallback.value = exp.fastIntentConfig && exp.fastIntentConfig.onMatch
      ? (exp.fastIntentConfig.onMatch.fallback || 'REFERENCE_ONLY')
      : 'REFERENCE_ONLY';
    dom.formFastIntentMatch.value = stringifyJson(exp.fastIntentConfig ? exp.fastIntentConfig.match : null);
    dom.formArtifactJson.value = stringifyJson(exp.artifact);
    dom.formPropertiesJson.value = stringifyJson(exp.properties);

    openModal(t('importExperienceModal'), false);
  }

  async function submitSkillImport() {
    if (currentImportMode === 'upload') {
      if (!selectedFile) {
        showToast(t('toastNoFileSelected'), 'error');
        return;
      }
      try {
        // Directly call /import-package — preserves references/assets and creates
        // tool experience alongside react experience.
        const result = await api.importSkillPackage(selectedFile);
        renderImportResult(result);
        if (result.warnings && result.warnings.length) {
          showToast(result.warnings.join('; '), 'error');
        }
        showToast(t('toastImportSuccess') || 'Import successful', 'success');
        closeSkillImport();
        // Refresh list + open detail drawer on the newly created react experience
        if (result.importedId) {
          await loadExperiences();
          setTimeout(() => { openEditModal(result.importedId); }, 200);
        }
      } catch (err) {
        showToast(t('toastImportFailed', { message: err.message }), 'error');
      }
    } else {
      const content = dom.skillImportContent.value.trim();
      if (!content) {
        showToast(t('toastPasteSkillFirst'), 'error');
        return;
      }
      try {
        const preview = await api.previewSkill(content);
        openImportForm(preview);
      } catch (err) {
        showToast(t('toastImportFailed', { message: err.message }), 'error');
      }
    }
  }


  // ═══════════════════════════════════════════════════════
  // SKILL Export
  // ═══════════════════════════════════════════════════════

  async function exportAsSkill(id) {
    try {
      // 触发 zip 下载（无损还原 SKILL.md / references / assets / package.json）
      const url = API_BASE + '/skills/export-package/' + encodeURIComponent(id);
      const resp = await fetch(url);
      if (!resp.ok) {
        const text = await resp.text().catch(() => '');
        throw new Error(text || `HTTP ${resp.status}`);
      }
      const blob = await resp.blob();
      const filename = parseFilenameFromContentDisposition(resp.headers.get('content-disposition'))
        || (id + '.zip');
      triggerDownload(blob, filename);
      showToast(t('toastExportDownloaded', { filename }), 'success');
    } catch (err) {
      showToast(t('toastExportFailed', { message: err.message }), 'error');
    }
  }

  function parseFilenameFromContentDisposition(header) {
    if (!header) return null;
    // RFC 5987: filename*=UTF-8''xxx
    const starMatch = header.match(/filename\*=UTF-8''([^;]+)/i);
    if (starMatch) {
      try { return decodeURIComponent(starMatch[1]); } catch (_) { /* fallthrough */ }
    }
    const plainMatch = header.match(/filename="?([^";]+)"?/i);
    return plainMatch ? plainMatch[1] : null;
  }

  function triggerDownload(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function closeSkillExport() {
    dom.skillExportModal.hidden = true;
  }

  async function copySkillToClipboard() {
    try {
      await navigator.clipboard.writeText(dom.skillExportContent.value);
      showToast(t('toastCopied'), 'success');
    } catch (_) {
      // Fallback
      dom.skillExportContent.select();
      document.execCommand('copy');
      showToast(t('toastCopied'), 'success');
    }
  }

  async function batchExportSkills() {
    const type = state.activeTab === 'ALL' ? 'REACT' : state.activeTab;
    try {
      const result = await api.exportAllSkills(type);
      dom.skillExportContent.value = result.content || result;
      $('#skill-export-title').textContent = t('exportAllTitle', { type });
      dom.skillExportModal.hidden = false;
    } catch (err) {
      showToast(t('toastExportFailed', { message: err.message }), 'error');
    }
  }


  // ═══════════════════════════════════════════════════════
  // Tool Source Browser
  // ═══════════════════════════════════════════════════════

  async function toggleToolSource(sourceId) {
    if (state.expandedSource === sourceId) {
      state.expandedSource = null;
      renderToolSources();
      return;
    }

    state.expandedSource = sourceId;

    if (!state.sourceTools[sourceId]) {
      try {
        const tools = await api.listToolsFromSource(sourceId);
        state.sourceTools[sourceId] = tools || [];
      } catch (err) {
        showToast(t('toastLoadToolsFailed', { message: err.message }), 'error');
        state.sourceTools[sourceId] = [];
      }
    }

    renderToolSources();
  }

  function updateImportButton(sourceCard) {
    const checkboxes = sourceCard.querySelectorAll('.tool-list input[type="checkbox"]:not(:disabled)');
    const checked = sourceCard.querySelectorAll('.tool-list input[type="checkbox"]:checked:not(:disabled)');
    const btn = sourceCard.querySelector('[data-action="import-tools"]');
    if (btn) {
      btn.disabled = checked.length === 0;
      btn.textContent = t('importSelected', { count: checked.length });
    }
    const countEl = sourceCard.querySelector('.tool-list-selectable-count');
    if (countEl) {
      countEl.textContent = t('availableCount', { count: checkboxes.length });
    }
  }

  async function importSelectedTools(sourceId) {
    const sourceCard = document.querySelector(`[data-source-id="${sourceId}"]`);
    if (!sourceCard) return;

    const checked = sourceCard.querySelectorAll('.tool-list input[type="checkbox"]:checked:not(:disabled)');
    const names = Array.from(checked).map(cb => cb.value);
    if (names.length === 0) return;

    try {
      const result = await api.importTools(sourceId, names);
      const count = result.importedIds ? result.importedIds.length : names.length;
      showToast(t('toolsImportedCount', { count }), 'success');

      invalidateSourceCache(sourceId);
      await Promise.all([loadStats(), loadToolSources()]);
    } catch (err) {
      showToast(t('toastImportFailed', { message: err.message }), 'error');
    }
  }

  async function syncToolSource(sourceId) {
    try {
      const result = await api.syncToolSource(sourceId);
      const created = result.createdIds ? result.createdIds.length : 0;
      const updated = result.updatedIds ? result.updatedIds.length : 0;
      const deleted = result.deletedIds ? result.deletedIds.length : 0;
      invalidateSourceCache(sourceId);
      showToast(t('toastSourceSynced', { created, updated, deleted }), 'success');
      if (result.failures && result.failures.length) {
        showToast(t('toastSourceSyncPartial', { count: result.failures.length }), 'error');
      }
      await Promise.all([loadStats(), loadToolSources(), loadExperiences()]);
    } catch (err) {
      showToast(t('toastSourceSyncFailed', { message: err.message }), 'error');
    }
  }


  // ═══════════════════════════════════════════════════════
  // Event Binding
  // ═══════════════════════════════════════════════════════

  function bindEvents() {
    // Search
    dom.searchInput.addEventListener('input', handleSearch);

    // Keyboard shortcut for search
    document.addEventListener('keydown', (e) => {
      if (e.key === '/' && !isInputFocused()) {
        e.preventDefault();
        dom.searchInput.focus();
      }
      if (e.key === 'Escape') {
        if (!dom.editModal.hidden) closeModal();
        else if (!dom.skillImportModal.hidden) closeSkillImport();
        else if (!dom.skillExportModal.hidden) closeSkillExport();
        else if (!dom.confirmDialog.hidden) closeConfirm();
      }
    });

    // Tabs
    $$('.tab').forEach(tab => {
      tab.addEventListener('click', () => switchTab(tab.dataset.tab));
    });

    // Header buttons
    $('#btn-create').addEventListener('click', openCreateForm);
    $('#btn-import-skill').addEventListener('click', openSkillImport);
    dom.btnExportAll.addEventListener('click', batchExportSkills);
    dom.btnLanguageToggle.addEventListener('click', () => {
      state.locale = state.locale === 'zh' ? 'en' : 'zh';
      applyLocale();
      renderExperienceList();
      renderToolSources();
    });
    dom.tenantFilter.addEventListener('change', async (e) => {
      state.selectedTenant = e.target.value || '';
      state.pagination.page = 1;
      updateIncludeGlobalFilterState();
      if (state.activeTab !== 'SOURCES') {
        await loadStats();
        await loadExperiences();
      }
    });
    dom.includeGlobalFilter.addEventListener('change', async (e) => {
      state.includeGlobal = !!e.target.checked;
      state.pagination.page = 1;
      if (state.activeTab !== 'SOURCES') {
        await loadStats();
        await loadExperiences();
      }
    });

    // Pagination
    dom.btnPrev.addEventListener('click', () => {
      if (state.pagination.page > 1) {
        state.pagination.page--;
        loadExperiences();
      }
    });
    dom.btnNext.addEventListener('click', () => {
      const totalPages = Math.ceil(state.pagination.total / state.pagination.size);
      if (state.pagination.page < totalPages) {
        state.pagination.page++;
        loadExperiences();
      }
    });

    // Experience list — delegated events
    dom.experienceList.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-action]');
      if (!btn) return;
      const action = btn.dataset.action;
      const id = btn.dataset.id;

      if (action === 'edit') openEditModal(id);
      else if (action === 'delete') confirmDelete(id, btn.dataset.name, btn.dataset.sourceInfo);
      else if (action === 'export') exportAsSkill(id);
    });

    // Edit modal
    $('#modal-close').addEventListener('click', closeModal);
    $('#modal-cancel').addEventListener('click', closeModal);
    $('#modal-save').addEventListener('click', submitForm);
    dom.formType.addEventListener('change', updateAdvancedSections);
    dom.formTenantTrigger.addEventListener('click', (e) => {
      e.preventDefault();
      toggleFormTenantDropdown();
    });
    dom.formTenantOptions.addEventListener('click', (e) => {
      const option = e.target.closest('[data-tenant-option]');
      if (!option) return;
      toggleTenantSelection(option.dataset.tenantOption);
    });
    dom.formTenantSearch.addEventListener('input', renderFormTenantOptions);
    dom.formTenantTags.addEventListener('click', (e) => {
      const removeBtn = e.target.closest('[data-remove-tenant]');
      if (!removeBtn) return;
      e.stopPropagation();
      toggleTenantSelection(removeBtn.dataset.removeTenant);
    });
    document.addEventListener('click', (e) => {
      if (!dom.formTenantMultiSelect) return;
      if (!dom.formTenantMultiSelect.contains(e.target)) {
        toggleFormTenantDropdown(false);
      }
    });
    dom.editModal.addEventListener('click', (e) => {
      if (e.target === dom.editModal) closeModal();
    });
    // Prevent form submit
    $('#experience-form').addEventListener('submit', (e) => e.preventDefault());

    // SKILL Import modal
    $('#btn-skill-preview').addEventListener('click', previewSkillImport);
    $('#btn-skill-import-submit').addEventListener('click', submitSkillImport);

    // Import tab switching
    dom.tabUploadFile.addEventListener('click', () => switchImportTab('upload'));
    dom.tabPasteText.addEventListener('click', () => switchImportTab('paste'));

    // Upload dropzone events
    dom.skillUploadDropzone.addEventListener('click', (e) => {
      if (e.target.closest('#upload-file-clear')) return;
      dom.skillUploadInput.click();
    });
    dom.skillUploadInput.addEventListener('change', (e) => {
      if (e.target.files.length > 0) handleFileSelect(e.target.files[0]);
    });
    dom.uploadFileClear.addEventListener('click', (e) => {
      e.stopPropagation();
      clearFileSelection();
    });
    dom.skillUploadDropzone.addEventListener('dragover', (e) => {
      e.preventDefault();
      dom.skillUploadDropzone.classList.add('dragover');
    });
    dom.skillUploadDropzone.addEventListener('dragleave', () => {
      dom.skillUploadDropzone.classList.remove('dragover');
    });
    dom.skillUploadDropzone.addEventListener('drop', (e) => {
      e.preventDefault();
      dom.skillUploadDropzone.classList.remove('dragover');
      if (e.dataTransfer.files.length > 0) handleFileSelect(e.dataTransfer.files[0]);
    });

    // SKILL Export modal
    $('#btn-copy-skill').addEventListener('click', copySkillToClipboard);

    // Confirm dialog
    $('#confirm-cancel').addEventListener('click', closeConfirm);
    $('#confirm-ok').addEventListener('click', executeDelete);
    dom.confirmDialog.addEventListener('click', (e) => {
      if (e.target === dom.confirmDialog) closeConfirm();
    });

    // Generic close buttons on modals
    $$('[data-close]').forEach(btn => {
      btn.addEventListener('click', () => {
        const target = document.getElementById(btn.dataset.close);
        if (target) target.hidden = true;
      });
    });

    // Tool source delegated events
    dom.toolSourcesList.addEventListener('click', (e) => {
      const toggleBtn = e.target.closest('[data-action="toggle-source"]');
      if (toggleBtn) {
        toggleToolSource(toggleBtn.dataset.source);
        return;
      }

      const importBtn = e.target.closest('[data-action="import-tools"]');
      if (importBtn) {
        importSelectedTools(importBtn.dataset.source);
        return;
      }

      const syncBtn = e.target.closest('[data-action="sync-source"]');
      if (syncBtn) {
        syncToolSource(syncBtn.dataset.source);
        return;
      }

      const selectAllBtn = e.target.closest('[data-action="select-all"]');
      if (selectAllBtn) {
        const card = selectAllBtn.closest('.tool-source-card');
        if (card) {
          card.querySelectorAll('.tool-list input[type="checkbox"]:not(:disabled)').forEach(cb => cb.checked = true);
          updateImportButton(card);
        }
        return;
      }

      const deselectAllBtn = e.target.closest('[data-action="deselect-all"]');
      if (deselectAllBtn) {
        const card = deselectAllBtn.closest('.tool-source-card');
        if (card) {
          card.querySelectorAll('.tool-list input[type="checkbox"]:not(:disabled)').forEach(cb => cb.checked = false);
          updateImportButton(card);
        }
        return;
      }
    });

    // Tool checkbox change
    dom.toolSourcesList.addEventListener('change', (e) => {
      if (e.target.type === 'checkbox') {
        const card = e.target.closest('.tool-source-card');
        if (card) updateImportButton(card);
      }
    });
  }

  function isInputFocused() {
    const el = document.activeElement;
    return el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT');
  }


  // ═══════════════════════════════════════════════════════
  // Initialization
  // ═══════════════════════════════════════════════════════

  async function init() {
    cacheDom();
    applyLocale();
    bindEvents();

    await loadTenants();
    await loadStats();
    await switchTab(state.activeTab);
  }

  document.addEventListener('DOMContentLoaded', init);

  return { init };
})();
