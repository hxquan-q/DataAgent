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

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 枚举值校验注解。
 * <p>
 * 用于验证字段或参数值是否为指定枚举类的合法值。
 * 可通过 {@link #method()} 指定使用枚举的哪个方法（如 {@code name()}、{@code getCode()}）
 * 来提取合法值集合进行比对。
 *
 * @see InEnumValidator
 */
@Documented
@Constraint(validatedBy = InEnumValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface InEnum {

	/** 指定用于校验的枚举类 */
	Class<? extends Enum<?>> value();

	/** 指定判断方法，默认为 {@code name}，也可指定为 {@code getCode} 等 */
	String method() default "name";

	/** 校验失败时的提示消息 */
	String message() default "变量值必须是指定枚举值之一";

	/** 校验分组 */
	Class<?>[] groups() default {};

	/** 校验负载信息 */
	Class<? extends Payload>[] payload() default {};

}
