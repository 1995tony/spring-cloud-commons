/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.cloud.context.refresh;

import java.util.Arrays;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.bootstrap.BootstrapApplicationListener;
import org.springframework.cloud.bootstrap.BootstrapConfigFileApplicationListener;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.springframework.cloud.util.PropertyUtils.BOOTSTRAP_ENABLED_PROPERTY;

/**
 * @author Dave Syer
 * @author Venil Noronha
 */
public class LegacyContextRefresher extends ContextRefresher {

	public LegacyContextRefresher(ConfigurableApplicationContext context, RefreshScope scope) {
		super(context, scope);
	}

	@Override
	protected void updateEnvironment() {
		addConfigFilesToEnvironment();
	}

	/* For testing. */ ConfigurableApplicationContext addConfigFilesToEnvironment() {
		ConfigurableApplicationContext capture = null;
		try {
//			從上下文拿出 Environment 物件, copy 一份
			StandardEnvironment environment = copyEnvironment(getContext().getEnvironment());
			SpringApplicationBuilder builder = new SpringApplicationBuilder(Empty.class)
					// banner和 web 都關閉，因為只是想單純利用新的 Spring 上下文構造一個新的 Environment
					.properties(BOOTSTRAP_ENABLED_PROPERTY + "=true").bannerMode(Banner.Mode.OFF)
					.web(WebApplicationType.NONE)
					// 傳入我們剛剛 copy 的 Environment 例項
					.environment(environment);
			// Just the listeners that affect the environment (e.g. excluding logging
			// listener because it has side effects)
			builder.application().setListeners(
					Arrays.asList(new BootstrapApplicationListener(), new BootstrapConfigFileApplicationListener()));
			// 啟動上下文
			capture = builder.run();
			if (environment.getPropertySources().contains(REFRESH_ARGS_PROPERTY_SOURCE)) {
				environment.getPropertySources().remove(REFRESH_ARGS_PROPERTY_SOURCE);
			}
			// 這個時候，通過上下文 SpringIOC 的啟動, 剛剛 Environment 物件就變成帶有最新配置值的 Environment 了
			// 獲取舊的外部化配置列表
			MutablePropertySources target = getContext().getEnvironment().getPropertySources();
			String targetName = null;
			// 遍歷這個最新的 Environment 外部化配置列表
			for (PropertySource<?> source : environment.getPropertySources()) {
				String name = source.getName();
				if (target.contains(name)) {
					targetName = name;
				}
				// 某些配置源不做替換，讀者自行檢視原始碼
				// 一般的配置源都會進入 if 語句
				if (!this.standardSources.contains(name)) {
					if (target.contains(name)) {
						target.replace(name, source);
					}
					else {
						if (targetName != null) {
							// 用新的配置替換舊的配置
							target.addAfter(targetName, source);
							// update targetName to preserve ordering
							targetName = name;
						}
						else {
							// targetName was null so we are at the start of the list
							target.addFirst(source);
							targetName = name;
						}
					}
				}
			}
		}
		finally {
			ConfigurableApplicationContext closeable = capture;
			while (closeable != null) {
				try {
					closeable.close();
				}
				catch (Exception e) {
					// Ignore;
				}
				if (closeable.getParent() instanceof ConfigurableApplicationContext) {
					closeable = (ConfigurableApplicationContext) closeable.getParent();
				}
				else {
					break;
				}
			}
		}
		return capture;
	}

}
