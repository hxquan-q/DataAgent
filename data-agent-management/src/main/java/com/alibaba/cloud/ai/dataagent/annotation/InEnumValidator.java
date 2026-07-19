/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link InEnum} 注解的校验器实现。
 * <p>
 * 在初始化阶段通过反射读取目标枚举类的合法值集合，在校验阶段检查输入值是否属于该集合。
 */
public class InEnumValidator implements ConstraintValidator<InEnum, Object> {

	/** 允许的合法值集合 */
	private final Set<Object> allowedValues = new HashSet<>();

	/**
	 * 初始化校验器，根据注解配置提取枚举合法值。
	 * @param annotation {@link InEnum} 注解实例
	 */
	@Override
	public void initialize(InEnum annotation) {
		Class<? extends Enum<?>> enumClass = annotation.value();
		String methodName = annotation.method();

		Enum<?>[] enums = enumClass.getEnumConstants();

		for (Enum<?> enumVal : enums) {
			try {
				// 如果是默认的 "name"，直接获取枚举名称
				if ("name".equals(methodName)) {
					allowedValues.add(enumVal.name());
				}
				else {
					// 否则利用反射调用指定方法（如 getCode）获取值
					Method method = enumClass.getMethod(methodName);
					method.setAccessible(true);
					Object val = method.invoke(enumVal);
					allowedValues.add(val);
				}
			}
			catch (Exception e) {
				throw new RuntimeException("校验注解初始化失败，无法获取枚举方法: " + methodName, e);
			}
		}
	}

	/**
	 * 校验输入值是否在合法枚举值集合中。
	 * @param value 待校验的值
	 * @param context 校验上下文
	 * @return 值合法或为空时返回 {@code true}，否则返回 {@code false}
	 */
	@Override
	public boolean isValid(Object value, ConstraintValidatorContext context) {
		// 允许为空（空值检查通常由 @NotNull 处理）
		if (value == null) {
			return true;
		}

		// 空字符串也允许通过
		if (value instanceof String && ((String) value).isEmpty()) {
			return true;
		}

		// 核心校验：检查值是否在合法集合中
		return allowedValues.contains(value);
	}

}
