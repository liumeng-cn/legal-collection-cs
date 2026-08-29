-- ============================================================
-- 08_test_data.sql —— 扩展测试数据（幂等，可重复执行）
-- 业务数据：多债务人 / 多案件（三种状态）/ 多案件绑定 / 还款记录
-- 知识库文档：贴近真实法催场景（法律依据 + 业务规则 + 作业规范），
--             覆盖公共 / 内部 / 案件专属三类权限
-- 向量化：需清空 document_chunk 后重启应用自动重建
-- ============================================================

\c auth

INSERT INTO debtor (id, name, id_card, phone) VALUES
  (3, '王五', '110101199303033456', '13800003333'),
  (4, '赵六', '110101199404044567', '13800004444'),
  (5, '孙七', '110101199505055678', '13800005555'),
  (6, '周八', '110101199606066789', '13800006666')
ON CONFLICT (id_card) DO NOTHING;

INSERT INTO debtor_case_binding (id, debtor_id, case_id) VALUES
  (3, 3, 3),
  (4, 4, 4),
  (5, 5, 5),
  (6, 1, 6),
  (7, 2, 7)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('debtor', 'id'), (SELECT max(id) FROM debtor));
SELECT setval(pg_get_serial_sequence('debtor_case_binding', 'id'), (SELECT max(id) FROM debtor_case_binding));

\c business

INSERT INTO case_info (id, case_no, debtor_id, status, amount_total) VALUES
  (3, 'LC20240003', 3, 'COLLECTING', 30000.00),
  (4, 'LC20240004', 4, 'OVERDUE',   120000.00),
  (5, 'LC20240005', 5, 'SETTLED',   20000.00),
  (6, 'LC20240006', 1, 'OVERDUE',   60000.00),
  (7, 'LC20240007', 2, 'COLLECTING', 15000.00)
ON CONFLICT (case_no) DO NOTHING;

INSERT INTO debt_detail (id, case_id, principal, interest, fee) VALUES
  (3, 3, 20000.00, 7000.00,  3000.00),
  (4, 4, 90000.00, 25000.00, 5000.00),
  (5, 5, 15000.00, 4000.00,  1000.00),
  (6, 6, 45000.00, 12000.00, 3000.00),
  (7, 7, 10000.00, 4000.00,  1000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO repayment_record (id, case_id, amount, channel, repaid_at) VALUES
  (4, 3, 2000.00,  'WE_CHAT',       now() - interval '20 days'),
  (5, 4, 5000.00,  'ALIPAY',        now() - interval '15 days'),
  (6, 6, 8000.00,  'BANK_TRANSFER', now() - interval '12 days'),
  (7, 7, 3000.00,  'WE_CHAT',       now() - interval '8 days'),
  (8, 5, 20000.00, 'BANK_TRANSFER', now() - interval '3 days')
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('case_info', 'id'), (SELECT max(id) FROM case_info));
SELECT setval(pg_get_serial_sequence('debt_detail', 'id'), (SELECT max(id) FROM debt_detail));
SELECT setval(pg_get_serial_sequence('repayment_record', 'id'), (SELECT max(id) FROM repayment_record));

\c knowledge

-- 清掉旧测试文档（id >= 6），重新插入真实内容
DELETE FROM document WHERE id >= 6;

-- 公共文档（所有角色可见）
INSERT INTO document (id, title, content, allowed_roles, case_id) VALUES
  (6, '逾期利息与违约金计算规则',
   '根据《民法典》第六百八十条，禁止高利放贷，借款利率不得违反国家有关规定。民间借贷的逾期利率司法保护上限为合同成立时一年期贷款市场报价利率（LPR）的四倍。本平台逾期利息按借款合同约定的利率按日计息，自逾期之日起计算至实际清偿之日止，违约金与利息合计不得超过法律保护上限。示例：借款本金四万元，约定年利率百分之二十四，日利率约万分之六点六，逾期三十天产生利息约八百元，具体以合同及催收系统显示金额为准。',
   NULL, NULL),
  (7, '还款方式与到账时效',
   '本平台支持银行转账、微信支付、支付宝三种还款方式。还款请转账至合同约定的对公还款账户，并在附言注明案件编号，以便系统自动对账。银行转账一般一到三个工作日到账并更新还款状态，微信与支付宝一般实时到账。还款成功后请保留转账凭证，如三个工作日后仍未更新状态，请携带凭证联系承办催收人员核对。',
   NULL, NULL),
  (8, '协商还款与个性化分期申请',
   '债务人如因失业、重大疾病、家庭变故等原因暂时无力一次性清偿，可向平台申请协商还款或个性化分期。申请需提交身份证复印件、收入证明或困难证明、还款计划书等材料。平台将根据欠款金额、逾期时长、债务人还款能力与意愿综合评估分期期数与每期金额。协商一致的需签署分期还款协议，未经审批任何人不得口头承诺减免或延期。',
   NULL, NULL),
  (9, '征信影响与信用修复',
   '根据《征信业管理条例》，逾期记录自欠款结清之日起保留五年，五年后由征信机构自动删除。逾期未结清期间，不良记录将持续影响个人征信，可能导致后续贷款、信用卡申请受阻。债务人应及时结清欠款，避免长期逾期形成呆账。已结清的，可向承办机构申请出具结清证明以便办理后续业务。',
   NULL, NULL),
  (10, '法律诉讼与执行流程',
   '协商无效且长期拒不还款的，平台可能委托律师事务所向有管辖权的人民法院提起诉讼。流程包括立案、送达、开庭审理、判决、执行。判决生效后债务人仍不履行的，法院可采取查封、冻结、限制高消费、纳入失信被执行人名单等强制执行措施。普通债权的诉讼时效为三年，自权利人知道权利受损之日起计算。',
   NULL, NULL),
  (11, '逾期是否会被追究刑事责任',
   '一般民事借贷逾期属于民事纠纷，不构成犯罪。但以非法占有为目的、恶意逃废债且情节严重的，可能涉嫌拒不执行判决、裁定罪或诈骗类犯罪。债务人应积极履行还款义务或主动协商，避免因消极对抗、转移财产等行为使民事纠纷升级为刑事案件。',
   NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- 内部文档（仅 STAFF / SRE 可见）
INSERT INTO document (id, title, content, allowed_roles, case_id) VALUES
  (12, '催收作业规范',
   '催收作业时间限定为每日八时至二十一时，禁止在非工作时段拨打。同一债务人每日外呼原则上不超过三次。通话须先核实债务人身份与案件编号，再说明欠款金额与逾期后果。严禁辱骂、威胁、恐吓、骚扰性语言；严禁冒充司法机关、律师或公职人员；严禁向债务人亲属、同事等第三方透露债务信息；首次通话须明确告知录音。',
   ARRAY['STAFF','SRE']::text[], NULL),
  (13, '减免与分期审批权限',
   '减免本金、利息、费用的审批实行分级授权：减免金额五千元以下由催收主管审批，五千至两万元由部门经理审批，两万元以上须报风控与合规联合审批。个性化分期期数原则上不超过六十期。所有减免与分期须留存审批单与完整沟通记录，未经审批的承诺一律无效并追究相关人员责任。',
   ARRAY['STAFF','SRE']::text[], NULL),
  (14, '个人信息保护与合规红线',
   '根据《个人信息保护法》，债务人信息仅限用于本案件催收，不得泄露、出售或用于其他用途。外呼须使用带录音的话务系统并事先告知。禁止通过非法渠道获取债务人通讯录、定位信息或家庭关系信息。违规者按严重违反公司制度处理，并可能承担相应法律责任。',
   ARRAY['STAFF','SRE']::text[], NULL)
ON CONFLICT (id) DO NOTHING;

-- 案件专属文档（仅该案件债务人 + STAFF/SRE 可见）
INSERT INTO document (id, title, content, allowed_roles, case_id) VALUES
  (15, 'LC20240001 案件还款方案',
   '该案件支持分十二期还款，每期还款约四千一百元，最后一期结清余款。债务人需在签署分期协议后三个工作日内完成首期还款，之后按月等额还款。按期履约期间利息停止增长，如再次逾期则分期方案自动失效，恢复全额催收。',
   NULL, 1),
  (16, 'LC20240002 案件协商记录',
   '该案件债务人已提交困难证明并申请延期还款，目前审核中。审核要点为困难证明真实性与还款意愿。审核通过后将以短信与电话方式通知债务人新的还款安排，未通过则维持原还款要求。',
   NULL, 2),
  (17, 'LC20240004 案件催收方案',
   '该案件欠款金额较大且逾期时间较长，建议优先电话联系债务人说明法律后果与征信影响，争取一次性或大额分期还款。若两周内无进展，升级为上门催收并同步准备诉讼材料，必要时启动诉讼流程。',
   NULL, 4)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('document', 'id'), (SELECT max(id) FROM document));
