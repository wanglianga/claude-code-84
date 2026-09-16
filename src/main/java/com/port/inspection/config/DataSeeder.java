package com.port.inspection.config;

import com.port.inspection.model.*;
import com.port.inspection.model.enums.*;
import com.port.inspection.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 演示数据初始化：测试账号（逐角色）、商家、各状态包裹、申报单、查验指令、税费、催件、赔付。
 * 仅当 users 表为空且 SEED_DEMO_DATA=true 时执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final ParcelRepository parcelRepository;
    private final ParcelEventRepository eventRepository;
    private final PrecheckResultRepository precheckRepository;
    private final DeclarationRepository declarationRepository;
    private final DeclarationParticipantRepository participantRepository;
    private final TaxRecordRepository taxRecordRepository;
    private final InspectionOrderRepository inspectionOrderRepository;
    private final ConsumerUrgeRepository urgeRepository;
    private final CompensationRepository compensationRepository;
    private final CustomsTaskRepository customsTaskRepository;
    private final BatchRepository batchRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-demo-data:true}")
    private boolean seedDemoData;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedDemoData || userRepository.count() > 0) {
            return;
        }
        log.info("初始化演示数据...");

        // ---------- 商家 ----------
        Merchant m1 = merchant("M001", "杭州跨贸电子商务有限公司", RiskLevel.LOW, 10, 100, false, 0);
        Merchant m2 = merchant("M002", "深圳海淘供应链有限公司", RiskLevel.HIGH, 50, 20, true, 3);

        // ---------- 用户（逐角色测试账号） ----------
        user("admin", "admin123", "系统管理员", Role.ADMIN, null, null);
        user("merchant1", "merchant123", "跨贸商家-小王", Role.MERCHANT, m1.getId(), null);
        user("merchant2", "merchant123", "海淘商家-小李", Role.MERCHANT, m2.getId(), null);
        user("warehouse1", "warehouse123", "口岸仓管-老张", Role.WAREHOUSE, null, null);
        user("broker1", "broker123", "报关员-老陈", Role.BROKER, null, null);
        user("cs1", "cs123", "客服-小周", Role.CS, null, null);
        user("customs1", "customs123", "海关关员-老吴", Role.CUSTOMS, null, null);
        user("finance1", "finance123", "财务-小郑", Role.FINANCE, null, null);
        user("consumer1", "consumer123", "消费者张三", Role.CONSUMER, null, "330106199501011234");

        String consumerIdCard = "330106199501011234";

        // ---------- 演示包裹 ----------
        // 1. 刚入仓，待预检（批次 BATCH001）
        Parcel p1 = parcel("WB20260001", m1, "0402109000", "婴幼儿配方奶粉", "218.00", 2,
                "李四", "110101199203074321", "13800000001", "BATCH001", "A-01-03", "顺丰国际",
                PackageStatus.RECEIVED, null);
        event(p1, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "包裹入仓，仓位 A-01-03");

        // 2. 预检通过，待申报
        Parcel p2 = parcel("WB20260002", m1, "3304990099", "保湿面霜", "268.00", 1,
                "王五", "310104198812125566", "13800000002", null, "B-02-11", "中通国际",
                PackageStatus.PRECHECK_PASSED, null);
        event(p2, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "包裹入仓");
        event(p2, PackageStatus.RECEIVED, PackageStatus.PRECHECK_PASSED, "申报前检查", "报关员-老陈", "BROKER", "申报前检查通过");

        // 3. 已提交海关，等待回执（含待处理海关任务，启动后自动处理）
        Parcel p3 = parcel("WB20260003", m1, "8517121000", "智能手机", "3299.00", 1,
                "赵六", "440305199506301234", "13800000003", null, "C-03-05", "顺丰国际",
                PackageStatus.CUSTOMS_REVIEW, null);
        event(p3, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "包裹入仓");
        event(p3, PackageStatus.RECEIVED, PackageStatus.PRECHECK_PASSED, "申报前检查", "报关员-老陈", "BROKER", "申报前检查通过");
        Declaration d3 = declaration(p3, m1, DeclarationStatus.SUBMITTED, "300.02");
        event(p3, PackageStatus.PRECHECK_PASSED, PackageStatus.CUSTOMS_REVIEW, "申报提交", "报关员-老陈", "BROKER",
                "申报单 " + d3.getDeclarationNo() + " 已提交海关审单");
        tax(d3, p3, "跨境电商综合税", "300.02", TaxStatus.PENDING);
        CustomsTask task = new CustomsTask();
        task.setDeclarationId(d3.getId());
        task.setTaskType("REVIEW");
        task.setExecuteAfter(LocalDateTime.now().plusSeconds(3));
        customsTaskRepository.save(task);

        // 4. 海关查验中（查验指令待仓库执行，税费已缴）
        Parcel p4 = parcel("WB20260004", m1, "6203429000", "男士休闲裤", "139.00", 3,
                "孙七", "320506199911204455", "13800000004", null, "A-05-02", "圆通国际",
                PackageStatus.INSPECTION, null);
        event(p4, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "包裹入仓");
        event(p4, PackageStatus.RECEIVED, PackageStatus.PRECHECK_PASSED, "申报前检查", "报关员-老陈", "BROKER", "申报前检查通过");
        Declaration d4 = declaration(p4, m1, DeclarationStatus.INSPECTION_REQUIRED, "37.95");
        event(p4, PackageStatus.PRECHECK_PASSED, PackageStatus.CUSTOMS_REVIEW, "申报提交", "报关员-老陈", "BROKER", "申报单已提交");
        event(p4, PackageStatus.CUSTOMS_REVIEW, PackageStatus.INSPECTION, "海关布控", "系统", "SYSTEM", "海关下达查验指令");
        tax(d4, p4, "跨境电商综合税", "37.95", TaxStatus.PAID);
        InspectionOrder order4 = new InspectionOrder();
        order4.setOrderNo("INS" + System.currentTimeMillis());
        order4.setDeclarationId(d4.getId());
        order4.setParcelId(p4.getId());
        order4.setInstruction("海关布控查验：开箱核对商品与申报信息，拍照留证");
        order4.setIssuedBy("海关审单系统");
        inspectionOrderRepository.save(order4);

        // 5. 已放行，待派送
        Parcel p5 = parcel("WB20260005", m1, "0901210000", "蓝山咖啡豆", "86.00", 4,
                "周八", "330203199008157788", "13800000005", null, "D-01-08", "中通国际",
                PackageStatus.RELEASED, null);
        event(p5, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "包裹入仓");
        event(p5, PackageStatus.RECEIVED, PackageStatus.PRECHECK_PASSED, "申报前检查", "报关员-老陈", "BROKER", "申报前检查通过");
        Declaration d5 = declaration(p5, m1, DeclarationStatus.RELEASED, "31.32");
        event(p5, PackageStatus.PRECHECK_PASSED, PackageStatus.CUSTOMS_REVIEW, "申报提交", "报关员-老陈", "BROKER", "申报单已提交");
        event(p5, PackageStatus.CUSTOMS_REVIEW, PackageStatus.RELEASED, "海关放行", "海关关员-老吴", "CUSTOMS", "海关审结且税费缴清，包裹放行");
        tax(d5, p5, "跨境电商综合税", "31.32", TaxStatus.PAID);

        // 6. 派送中（消费者张三的包裹，含一条未处理催件）
        Parcel p6 = parcel("WB20260006", m1, "0402109000", "婴幼儿配方奶粉", "218.00", 1,
                "张三", consumerIdCard, "13800000006", null, "D-02-01", "顺丰国际",
                PackageStatus.DELIVERING, null);
        event(p6, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "包裹入仓");
        event(p6, PackageStatus.RECEIVED, PackageStatus.PRECHECK_PASSED, "申报前检查", "报关员-老陈", "BROKER", "申报前检查通过");
        Declaration d6 = declaration(p6, m1, DeclarationStatus.RELEASED, "19.84");
        event(p6, PackageStatus.PRECHECK_PASSED, PackageStatus.CUSTOMS_REVIEW, "申报提交", "报关员-老陈", "BROKER", "申报单已提交");
        event(p6, PackageStatus.CUSTOMS_REVIEW, PackageStatus.RELEASED, "海关放行", "海关关员-老吴", "CUSTOMS", "海关放行");
        event(p6, PackageStatus.RELEASED, PackageStatus.DELIVERING, "放行派送", "口岸仓管-老张", "WAREHOUSE", "海关放行，转国内物流派送");
        tax(d6, p6, "跨境电商综合税", "19.84", TaxStatus.PAID);
        ConsumerUrge urge = new ConsumerUrge();
        urge.setParcelId(p6.getId());
        urge.setConsumerName("消费者张三");
        urge.setMessage("包裹显示派送中两天了，请帮忙催一下");
        urgeRepository.save(urge);
        event(p6, PackageStatus.DELIVERING, PackageStatus.DELIVERING, "消费者催件", "消费者张三", "CONSUMER", "包裹显示派送中两天了，请帮忙催一下");

        // 7. 查验不通过待补材料（消费者张三的包裹，缺少认证）
        Parcel p7 = parcel("WB20260007", m1, "3304990099", "进口精华液", "320.00", 1,
                "张三", consumerIdCard, "13800000007", null, "A-07-07", "顺丰国际",
                PackageStatus.SUPPLEMENT_REQUIRED, null);
        event(p7, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "包裹入仓");
        event(p7, PackageStatus.RECEIVED, PackageStatus.PRECHECK_PASSED, "申报前检查", "报关员-老陈", "BROKER", "申报前检查通过");
        Declaration d7 = declaration(p7, m1, DeclarationStatus.SUPPLEMENT_REQUIRED, "73.92");
        d7.setFailReason("MISSING_CERT");
        d7.setSupplementNote("请补充化妆品备案凭证后重新提交");
        declarationRepository.save(d7);
        event(p7, PackageStatus.PRECHECK_PASSED, PackageStatus.CUSTOMS_REVIEW, "申报提交", "报关员-老陈", "BROKER", "申报单已提交");
        event(p7, PackageStatus.CUSTOMS_REVIEW, PackageStatus.INSPECTION, "海关布控", "系统", "SYSTEM", "海关下达查验指令");
        event(p7, PackageStatus.INSPECTION, PackageStatus.SUPPLEMENT_REQUIRED, "查验不通过", "海关关员-老吴", "CUSTOMS",
                "缺少认证；处置：补充材料");
        tax(d7, p7, "跨境电商综合税", "73.92", TaxStatus.PENDING);
        InspectionOrder order7 = new InspectionOrder();
        order7.setOrderNo("INS" + (System.currentTimeMillis() + 1));
        order7.setDeclarationId(d7.getId());
        order7.setParcelId(p7.getId());
        order7.setInstruction("海关布控查验：开箱核对商品与申报信息");
        order7.setIssuedBy("海关审单系统");
        order7.setStatus(InspectionOrderStatus.COMPLETED);
        order7.setVerdict(InspectionVerdict.FAIL);
        order7.setFailReason(FailReason.MISSING_CERT);
        order7.setFailAction(FailAction.SUPPLEMENT);
        order7.setCompletedAt(LocalDateTime.now().minusHours(2));
        inspectionOrderRepository.save(order7);

        // 8. 预检失败（禁止进口商品）
        Parcel p8 = parcel("WB20260008", m1, "9601900000", "象牙筷子礼盒", "580.00", 1,
                "吴九", "350102198703096677", "13800000008", null, "X-09-09", "圆通国际",
                PackageStatus.PRECHECK_FAILED, null);
        event(p8, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "包裹入仓");
        PrecheckResult pr8 = new PrecheckResult();
        pr8.setParcelId(p8.getId());
        pr8.setCheckType(CheckType.RESTRICTED_GOODS);
        pr8.setLevel(PrecheckLevel.FAIL);
        pr8.setMessage("禁止进口商品：象牙及其制品禁止进口");
        precheckRepository.save(pr8);
        event(p8, PackageStatus.RECEIVED, PackageStatus.PRECHECK_FAILED, "申报前检查", "报关员-老陈", "BROKER", "申报前检查未通过，禁止申报");

        // 9. 高风险商家的包裹（须提前上传票据才能申报）
        Parcel p9 = parcel("WB20260009", m2, "8517121000", "二手手机", "999.00", 1,
                "郑十", "440106199212125533", "13800000009", null, "E-01-01", "韵达国际",
                PackageStatus.RECEIVED, null);
        event(p9, null, PackageStatus.RECEIVED, "入仓登记", "海淘商家-小李", "MERCHANT", "包裹入仓（高风险商家）");

        // 10. 已签收（消费者张三）
        Parcel p10 = parcel("WB20260010", m1, "9503008900", "乐高积木玩具", "259.00", 1,
                "张三", consumerIdCard, "13800000010", null, "D-03-06", "顺丰国际",
                PackageStatus.DELIVERED, null);
        event(p10, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "包裹入仓");
        event(p10, PackageStatus.RECEIVED, PackageStatus.PRECHECK_PASSED, "申报前检查", "报关员-老陈", "BROKER", "申报前检查通过");
        Declaration d10 = declaration(p10, m1, DeclarationStatus.RELEASED, "23.57");
        event(p10, PackageStatus.PRECHECK_PASSED, PackageStatus.CUSTOMS_REVIEW, "申报提交", "报关员-老陈", "BROKER", "申报单已提交");
        event(p10, PackageStatus.CUSTOMS_REVIEW, PackageStatus.RELEASED, "海关放行", "海关关员-老吴", "CUSTOMS", "海关放行");
        event(p10, PackageStatus.RELEASED, PackageStatus.DELIVERING, "放行派送", "口岸仓管-老张", "WAREHOUSE", "转国内派送");
        event(p10, PackageStatus.DELIVERING, PackageStatus.DELIVERED, "签收", "口岸仓管-老张", "WAREHOUSE", "收件人已签收");
        tax(d10, p10, "跨境电商综合税", "23.57", TaxStatus.PAID);

        // 11-13. 批次 BATCH001 的其余包裹（含一个禁售品，演示批次异常隔离）
        Parcel p11 = parcel("WB20260011", m1, "2106909090", "深海鱼油胶囊", "168.00", 2,
                "钱一", "510107199405058899", "13800000011", "BATCH001", "A-01-04", "顺丰国际",
                PackageStatus.RECEIVED, null);
        event(p11, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "批次 BATCH001 入仓");
        Parcel p12 = parcel("WB20260012", m1, "6203429000", "女士连衣裙", "129.00", 1,
                "冯二", "420106199609104411", "13800000012", "BATCH001", "A-01-05", "中通国际",
                PackageStatus.RECEIVED, null);
        event(p12, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "批次 BATCH001 入仓");
        Parcel p13 = parcel("WB20260013", m1, "9601900000", "象牙手镯", "2600.00", 1,
                "陈三", "330106199708223344", "13800000013", "BATCH001", "A-01-06", "中通国际",
                PackageStatus.RECEIVED, null);
        event(p13, null, PackageStatus.RECEIVED, "入仓登记", "跨贸商家-小王", "MERCHANT", "批次 BATCH001 入仓");

        // 批次 BATCH001
        Batch batch = new Batch();
        batch.setBatchNo("BATCH001");
        batch.setMerchantId(m1.getId());
        batch.setTotalCount(4);
        batchRepository.save(batch);

        // 一条待审批赔付（演示财务流程）
        Compensation comp = new Compensation();
        comp.setParcelId(p6.getId());
        comp.setAmount(new BigDecimal("50.00"));
        comp.setReason("外箱挤压变形，内件完好，补偿消费者");
        comp.setResponsibleParty("LOGISTICS");
        comp.setCreatedBy("客服-小周");
        compensationRepository.save(comp);

        log.info("演示数据初始化完成：{} 个用户，{} 个包裹", userRepository.count(), parcelRepository.count());
    }

    private Merchant merchant(String code, String name, RiskLevel level, int ratio, int limit,
                              boolean requireDocs, int violations) {
        Merchant m = new Merchant();
        m.setCode(code);
        m.setName(name);
        m.setRiskLevel(level);
        m.setInspectionRatio(ratio);
        m.setBatchLimit(limit);
        m.setRequireAdvanceDocs(requireDocs);
        m.setViolationCount(violations);
        return merchantRepository.save(m);
    }

    private User user(String username, String password, String displayName, Role role, Long merchantId, String idCard) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setDisplayName(displayName);
        u.setRole(role);
        u.setMerchantId(merchantId);
        u.setIdCard(idCard);
        return userRepository.save(u);
    }

    private Parcel parcel(String waybill, Merchant m, String hs, String goods, String price, int qty,
                          String recipient, String idCard, String phone, String batchNo, String location,
                          String channel, PackageStatus status, Void unused) {
        Parcel p = new Parcel();
        p.setWaybillNo(waybill);
        p.setMerchantId(m.getId());
        p.setHsCode(hs);
        p.setGoodsName(goods);
        p.setDeclaredPrice(new BigDecimal(price));
        p.setQuantity(qty);
        p.setRecipientName(recipient);
        p.setRecipientIdCard(idCard);
        p.setRecipientPhone(phone);
        p.setBatchNo(batchNo);
        p.setWarehouseLocation(location);
        p.setLogisticsChannel(channel);
        p.setStatus(status);
        return parcelRepository.save(p);
    }

    private Declaration declaration(Parcel p, Merchant m, DeclarationStatus status, String taxAmount) {
        Declaration d = new Declaration();
        d.setDeclarationNo("DEC" + System.currentTimeMillis() + p.getId());
        d.setParcelId(p.getId());
        d.setMerchantId(m.getId());
        d.setStatus(status);
        d.setTaxAmount(new BigDecimal(taxAmount));
        d.setSubmittedBy("报关员-老陈");
        d.setSubmittedAt(LocalDateTime.now().minusHours(3));
        declarationRepository.save(d);
        // 六方协同
        addParticipant(d, "跨贸商家-小王", Role.MERCHANT);
        addParticipant(d, "口岸仓管-老张", Role.WAREHOUSE);
        addParticipant(d, "报关员-老陈", Role.BROKER);
        addParticipant(d, "客服-小周", Role.CS);
        addParticipant(d, "海关关员-老吴", Role.CUSTOMS);
        addParticipant(d, "财务-小郑", Role.FINANCE);
        return d;
    }

    private void addParticipant(Declaration d, String displayName, Role role) {
        userRepository.findFirstByRole(role).ifPresent(u -> {
            DeclarationParticipant dp = new DeclarationParticipant();
            dp.setDeclarationId(d.getId());
            dp.setUserId(u.getId());
            dp.setRole(role);
            dp.setDisplayName(displayName);
            participantRepository.save(dp);
        });
    }

    private void tax(Declaration d, Parcel p, String type, String amount, TaxStatus status) {
        TaxRecord t = new TaxRecord();
        t.setDeclarationId(d.getId());
        t.setParcelId(p.getId());
        t.setTaxType(type);
        t.setAmount(new BigDecimal(amount));
        t.setStatus(status);
        if (status == TaxStatus.PAID) {
            t.setPaidBy("财务-小郑");
            t.setPaidAt(LocalDateTime.now().minusHours(2));
        }
        taxRecordRepository.save(t);
    }

    private void event(Parcel p, PackageStatus from, PackageStatus to, String node,
                       String actor, String actorRole, String remark) {
        ParcelEvent e = new ParcelEvent();
        e.setParcelId(p.getId());
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setNode(node);
        e.setActor(actor);
        e.setActorRole(actorRole);
        e.setRemark(remark);
        eventRepository.save(e);
    }
}
