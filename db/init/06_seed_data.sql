-- ============================================================
-- Demo 种子数据（催员账号由后端 DataInitializer 用 BCrypt 初始化）
-- ============================================================

\c auth

INSERT INTO debtor (id, name, id_card, phone) VALUES
  (1, '张三', '110101199001011234', '13800001111'),
  (2, '李四', '110101199202022345', '13800002222');

INSERT INTO debtor_case_binding (debtor_id, case_id) VALUES
  (1, 1),
  (2, 2);

SELECT setval(pg_get_serial_sequence('debtor', 'id'), (SELECT max(id) FROM debtor));
SELECT setval(pg_get_serial_sequence('debtor_case_binding', 'id'), (SELECT max(id) FROM debtor_case_binding));

\c business

INSERT INTO case_info (id, case_no, debtor_id, status, amount_total) VALUES
  (1, 'LC20240001', 1, 'COLLECTING', 50000.00),
  (2, 'LC20240002', 2, 'OVERDUE',   80000.00);

INSERT INTO debt_detail (case_id, principal, interest, fee) VALUES
  (1, 40000.00, 8000.00,  2000.00),
  (2, 60000.00, 15000.00, 5000.00);

INSERT INTO repayment_record (case_id, amount, channel, repaid_at) VALUES
  (1, 5000.00, 'BANK_TRANSFER', now() - interval '30 days'),
  (1, 5000.00, 'BANK_TRANSFER', now() - interval '10 days'),
  (2, 3000.00, 'BANK_TRANSFER', now() - interval '5 days');

SELECT setval(pg_get_serial_sequence('case_info', 'id'), (SELECT max(id) FROM case_info));

\c knowledge

INSERT INTO document (title, content, allowed_roles, case_id) VALUES
  ('还款方式有哪些',
   '本平台支持银行转账、微信支付、支付宝等多种还款方式。请在还款前核对案件编号与金额，转账时备注案件编号，以便系统自动对账。还款后一般 1-3 个工作日内更新还款状态。',
   NULL, NULL),
  ('逾期会有什么后果',
   '逾期会产生违约金和利息，影响个人征信记录。若长期逾期且拒不还款，平台可能依法向法院提起诉讼，届时可能面临财产冻结、限制高消费等法律措施。建议尽快联系催收人员协商还款。',
   NULL, NULL),
  ('如何申请协商还款',
   '债务人如有还款困难，可主动联系承办催收人员或拨打客服热线，提交收入证明、困难说明等材料，申请延期或分期还款。平台会根据债务人实际情况与还款意愿进行审核。',
   NULL, NULL),
  ('能否分期还款',
   '符合条件的债务人可申请分期还款，具体期数与每期金额由平台根据欠款金额、债务人还款能力等综合评估确定。协商一致后需签署分期还款协议并按约定履行。',
   NULL, NULL),
  ('法律诉讼流程是怎样的',
   '若协商无效，平台可能委托律师事务所向法院提起诉讼。流程一般包括：立案、送达、开庭审理、判决、执行。判决生效后债务人仍不履行的，法院可依法采取强制执行措施。',
   NULL, NULL),
  ('催收内部话术（仅内部）',
   '催收员与债务人沟通时应遵循：先核实身份与案件编号，再说明欠款金额与逾期后果，最后引导协商还款。禁止威胁、辱骂、骚扰性语言；未经审批不得承诺减免或延期。',
   ARRAY['STAFF','SRE']::text[], NULL);

\c chat
-- conversation / message 表由运行时创建，无需种子数据
