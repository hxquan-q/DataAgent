/*
 * Copyright 2026 the original author or authors.
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

/**
 * @description 安全状态服务——凭据加密（AES-256-GCM）启停状态查询
 */

import axios from 'axios';
import type { ApiResponse } from '~/services/common/index';

/** 凭据加密状态 */
export interface CryptoStatus {
  /** 是否已启用加密 */
  enabled: boolean;
  /** 算法 */
  algorithm: string;
  /** 密文信封前缀 */
  envelopePrefix: string;
  /** 主密钥是否配置 */
  keyConfigured: string;
  /** 加密覆盖范围 */
  coverage: string;
}

/**
 * @description 安全状态业务类
 */
class SecurityService {
  /**
   * @description 查询凭据加密状态
   */
  async cryptoStatus(): Promise<CryptoStatus> {
    const response = await axios.get<ApiResponse<CryptoStatus>>('/api/security/crypto-status');
    return response.data.data;
  }
}

export default new SecurityService();
