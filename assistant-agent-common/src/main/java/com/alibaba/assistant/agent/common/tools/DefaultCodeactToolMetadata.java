/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.common.tools;

import com.alibaba.assistant.agent.common.enums.Language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CodeactToolMetadata 的默认实现。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class DefaultCodeactToolMetadata implements CodeactToolMetadata {

	private final List<Language> supportedLanguages;

	private final String targetClassName;

	private final String targetClassDescription;

	private final String codeInvocationTemplate;

	private final List<CodeExample> fewShots;

	private final boolean returnDirect;

	private final String displayName;

	private final List<String> aliases;

	private final boolean alwaysAvailable;

	private DefaultCodeactToolMetadata(Builder builder) {
		this.supportedLanguages = Collections.unmodifiableList(builder.supportedLanguages);
		this.targetClassName = builder.targetClassName;
		this.targetClassDescription = builder.targetClassDescription;
		this.codeInvocationTemplate = builder.codeInvocationTemplate;
		this.fewShots = Collections.unmodifiableList(builder.fewShots);
		this.returnDirect = builder.returnDirect;
		this.displayName = builder.displayName;
		this.aliases = Collections.unmodifiableList(builder.aliases);
		this.alwaysAvailable = builder.alwaysAvailable;
	}

	@Override
	public List<Language> supportedLanguages() {
		return supportedLanguages;
	}

	@Override
	public String targetClassName() {
		return targetClassName;
	}

	@Override
	public String targetClassDescription() {
		return targetClassDescription;
	}

	@Override
	@Deprecated
	public String codeInvocationTemplate() {
		return codeInvocationTemplate;
	}

	@Override
	public List<CodeExample> fewShots() {
		return fewShots;
	}

	@Override
	public boolean returnDirect() {
		return returnDirect;
	}

	@Override
	public String displayName() {
		return displayName;
	}

	@Override
	public List<String> aliases() {
		return aliases;
	}

	@Override
	public boolean alwaysAvailable() {
		return alwaysAvailable;
	}

	/**
	 * 创建构建器实例。
	 *
	 * @return 构建器
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * CodeactToolMetadata 构建器
	 */
	public static class Builder {

		private List<Language> supportedLanguages = new ArrayList<>();

		private String targetClassName = "";

		private String targetClassDescription = "";

		private String codeInvocationTemplate = "";

		private List<CodeExample> fewShots = new ArrayList<>();

		private boolean returnDirect = false;

		private String displayName = null;

		private List<String> aliases = new ArrayList<>();

		private boolean alwaysAvailable = false;

		/**
		 * 设置支持的语言列表。
		 *
		 * @param supportedLanguages 支持的语言列表
		 * @return 构建器
		 */
		public Builder supportedLanguages(List<Language> supportedLanguages) {
			this.supportedLanguages = new ArrayList<>(supportedLanguages);
			return this;
		}

		/**
		 * 添加支持的语言。
		 *
		 * @param language 语言
		 * @return 构建器
		 */
		public Builder addSupportedLanguage(Language language) {
			this.supportedLanguages.add(language);
			return this;
		}

		/**
		 * 设置目标类名。
		 *
		 * @param targetClassName 目标类名
		 * @return 构建器
		 */
		public Builder targetClassName(String targetClassName) {
			this.targetClassName = targetClassName;
			return this;
		}

		/**
		 * 设置目标类描述。
		 *
		 * @param targetClassDescription 目标类描述
		 * @return 构建器
		 */
		public Builder targetClassDescription(String targetClassDescription) {
			this.targetClassDescription = targetClassDescription;
			return this;
		}

		/**
		 * 设置代码调用模板。
		 *
		 * @param codeInvocationTemplate 代码调用模板
		 * @return 构建器
		 * @deprecated 使用 ParameterTree 替代
		 */
		@Deprecated
		public Builder codeInvocationTemplate(String codeInvocationTemplate) {
			this.codeInvocationTemplate = codeInvocationTemplate;
			return this;
		}

		/**
		 * 设置 few-shot 示例列表。
		 *
		 * @param fewShots few-shot 示例列表
		 * @return 构建器
		 */
		public Builder fewShots(List<CodeExample> fewShots) {
			this.fewShots = new ArrayList<>(fewShots);
			return this;
		}

		/**
		 * 添加 few-shot 示例。
		 *
		 * @param fewShot few-shot 示例
		 * @return 构建器
		 */
		public Builder addFewShot(CodeExample fewShot) {
			this.fewShots.add(fewShot);
			return this;
		}

		/**
		 * 设置是否直接返回。
		 *
		 * @param returnDirect 是否直接返回
		 * @return 构建器
		 */
		public Builder returnDirect(boolean returnDirect) {
			this.returnDirect = returnDirect;
			return this;
		}

		/**
		 * 设置显示名称。
		 *
		 * @param displayName 显示名称
		 * @return 构建器
		 */
		public Builder displayName(String displayName) {
			this.displayName = displayName;
			return this;
		}

		/**
		 * 设置别名列表。
		 *
		 * @param aliases 别名列表
		 * @return 构建器
		 */
		public Builder aliases(List<String> aliases) {
			this.aliases = new ArrayList<>(aliases);
			return this;
		}

		/**
		 * 添加别名。
		 *
		 * @param alias 别名
		 * @return 构建器
		 */
		public Builder addAlias(String alias) {
			this.aliases.add(alias);
			return this;
		}

		/**
		 * 设置工具是否始终可用（不受评估筛选影响）。
		 *
		 * <p>基础工具（如 reply_tools）应设置为 true，
		 * 这样无论评估筛选结果如何，这些工具都会包含在可用工具列表中。
		 *
		 * @param alwaysAvailable 是否始终可用
		 * @return 构建器
		 */
		public Builder alwaysAvailable(boolean alwaysAvailable) {
			this.alwaysAvailable = alwaysAvailable;
			return this;
		}

		/**
		 * 构建 CodeactToolMetadata 实例。
		 *
		 * @return CodeactToolMetadata 实例
		 */
		public DefaultCodeactToolMetadata build() {
			return new DefaultCodeactToolMetadata(this);
		}

	}

}

