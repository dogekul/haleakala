UPDATE stage_instance SET stage_name = CASE stage_code
  WHEN 'START' THEN '项目立项'
  WHEN 'REQUIREMENT' THEN '调研与启动'
  WHEN 'CUSTOM_DEV' THEN '方案与计划'
  WHEN 'GO_LIVE' THEN '开发与测试'
  WHEN 'TRIAL_HANDOVER' THEN '验证与发布'
  WHEN 'STANDARDIZATION' THEN '验收与结项'
  WHEN 'CLOSE' THEN '过程跟进'
  ELSE stage_name
END;
