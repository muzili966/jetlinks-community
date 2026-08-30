package org.jetlinks.community.tenant.service;

import lombok.AllArgsConstructor;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.tenant.entity.TenantInvoiceEntity;
import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.jetlinks.community.tenant.enums.TenantInvoiceStatus;
import org.jetlinks.community.tenant.enums.TenantOrderStatus;
import org.jetlinks.community.tenant.service.request.TenantInvoiceApplyRequest;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 发票申请: 校验订单可开票性 → 创建申请单 → 锁定订单;
 * 开具/驳回由平台管理员操作, 驳回释放订单允许重新申请.
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
public class TenantInvoiceService extends GenericReactiveCrudService<TenantInvoiceEntity, String> {

    private final TenantOrderService orderService;

    @Transactional
    public Mono<TenantInvoiceEntity> apply(TenantInvoiceApplyRequest request) {
        return orderService
            .findById(request.getOrderIdList())
            .collectList()
            .flatMap(orders -> {
                validateOrders(orders, request.getOrderIdList());
                TenantInvoiceEntity invoice = buildInvoice(orders, request);
                return insert(invoice)
                    .then(lockOrders(request.getOrderIdList(), invoice.getId()))
                    .thenReturn(invoice);
            });
    }

    /**
     * 可开票校验: 订单齐全、同一租户、已支付、未开票
     */
    private void validateOrders(List<TenantOrderEntity> orders, List<String> requested) {
        if (orders.size() != requested.size()) {
            throw new BusinessException("error.tenant_invoice_order_not_found", 404);
        }
        long tenants = orders.stream().map(TenantOrderEntity::getTenantId).distinct().count();
        if (tenants != 1) {
            throw new BusinessException("error.tenant_invoice_orders_cross_tenant", 400);
        }
        for (TenantOrderEntity order : orders) {
            if (order.getStatus() != TenantOrderStatus.paid) {
                throw new BusinessException("error.tenant_invoice_order_not_paid", 400, order.getId());
            }
            if (order.getInvoiceId() != null) {
                throw new BusinessException("error.tenant_invoice_order_already_invoiced", 400, order.getId());
            }
        }
    }

    private TenantInvoiceEntity buildInvoice(List<TenantOrderEntity> orders,
                                             TenantInvoiceApplyRequest request) {
        TenantInvoiceEntity invoice = new TenantInvoiceEntity();
        invoice.setId(org.hswebframework.web.id.IDGenerator.SNOW_FLAKE_STRING.generate());
        invoice.setTenantId(orders.get(0).getTenantId());
        invoice.setTenantName(orders.get(0).getTenantName());
        invoice.setOrderIdList(request.getOrderIdList());
        invoice.setTotalAmount(computeTotal(orders));
        invoice.setInvoiceType(request.getInvoiceType());
        invoice.setTitle(request.getTitle());
        invoice.setTaxNo(request.getTaxNo());
        invoice.setBankName(request.getBankName());
        invoice.setBankAccount(request.getBankAccount());
        invoice.setAddress(request.getAddress());
        invoice.setPhone(request.getPhone());
        invoice.setEmail(request.getEmail());
        invoice.setRemark(request.getRemark());
        invoice.setStatus(TenantInvoiceStatus.pending);
        return invoice;
    }

    static long computeTotal(List<TenantOrderEntity> orders) {
        return orders
            .stream()
            .mapToLong(o -> o.getTotalAmount() == null ? 0 : o.getTotalAmount())
            .sum();
    }

    private Mono<Void> lockOrders(List<String> orderIdList, String invoiceId) {
        return orderService
            .createUpdate()
            .set(TenantOrderEntity::getInvoiceId, invoiceId)
            .where()
            .in(TenantOrderEntity::getId, orderIdList)
            .execute()
            .then();
    }

    @Transactional
    public Mono<Void> issue(String invoiceId, String invoiceNo, String remark) {
        return transitPending(invoiceId, update -> update
            .set(TenantInvoiceEntity::getStatus, TenantInvoiceStatus.issued)
            .set(TenantInvoiceEntity::getInvoiceNo, invoiceNo)
            .set(TenantInvoiceEntity::getIssueTime, System.currentTimeMillis())
            .set(TenantInvoiceEntity::getRemark, remark));
    }

    /**
     * 红冲：已开具的发票作废。
     * <p>
     * 与驳回的区别：驳回发生在开具前（申请被拒），红冲发生在开具后（发票已开错）。
     * 两者都会释放订单，让租户可以重新申请。
     */
    @Transactional
    public Mono<Void> voidInvoice(String invoiceId, String reason) {
        return findById(invoiceId)
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_invoice_not_found", 404, invoiceId)))
            .filter(invoice -> invoice.getStatus() == TenantInvoiceStatus.issued)
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_invoice_not_issued", 400, invoiceId)))
            .flatMap(invoice -> createUpdate()
                .set(TenantInvoiceEntity::getStatus, TenantInvoiceStatus.voided)
                .set(TenantInvoiceEntity::getRejectReason, reason)
                .where(TenantInvoiceEntity::getId, invoiceId)
                .execute()
                .then())
            .then(unlockOrders(invoiceId));
    }

    @Transactional
    public Mono<Void> reject(String invoiceId, String reason) {
        return transitPending(invoiceId, update -> update
            .set(TenantInvoiceEntity::getStatus, TenantInvoiceStatus.rejected)
            .set(TenantInvoiceEntity::getRejectReason, reason))
            // 驳回后释放订单, 允许重新申请
            .then(unlockOrders(invoiceId));
    }

    private Mono<Void> transitPending(String invoiceId,
                                      java.util.function.UnaryOperator<org.hswebframework.ezorm.rdb.mapping.ReactiveUpdate<TenantInvoiceEntity>> setter) {
        return findById(invoiceId)
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_invoice_not_found", 404, invoiceId)))
            .filter(invoice -> invoice.getStatus() == TenantInvoiceStatus.pending)
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_invoice_not_pending", 400, invoiceId)))
            .flatMap(invoice -> setter
                .apply(createUpdate())
                .where(TenantInvoiceEntity::getId, invoiceId)
                .execute()
                .then());
    }

    private Mono<Void> unlockOrders(String invoiceId) {
        return orderService
            .createUpdate()
            .setNull(TenantOrderEntity::getInvoiceId)
            .where(TenantOrderEntity::getInvoiceId, invoiceId)
            .execute()
            .then();
    }
}
