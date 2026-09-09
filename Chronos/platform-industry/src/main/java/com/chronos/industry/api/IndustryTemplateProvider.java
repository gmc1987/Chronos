package com.chronos.industry.api;

/**
 * 行业模块只需要实现该契约即可被平台发现，平台层不反向依赖任何具体行业。
 */
public interface IndustryTemplateProvider {

    IndustryMetadata metadata();
}
