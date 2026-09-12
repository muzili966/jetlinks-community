package org.jetlinks.community.tenant.web;

import org.hswebframework.reactor.excel.WriterOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * 导出文件的字节流适配，替代有缺陷的 {@link WriterOperator#writeBuffer(Flux, int)}。
 * <p>
 * reactor-excel 1.0.6 的 {@code StreamUtils$1.write(byte[] b, int off, int len)} 里有个「优化」：
 * 当 {@code len == b.length} 时直接 {@code sink.next(b)} 发射原数组、不拷贝。
 * 而它外层套的正是 {@link BufferedOutputStream}，缓冲区写满时恰好满足这个条件，
 * 于是把内部缓冲区原样交出去，随后又继续复用它写入后续数据；
 * 下游 {@code bufferFactory().wrap} 是零拷贝，{@code Flux.create} 又只是把引用入队，
 * 真正消费时字节早被覆盖。
 * <p>
 * xlsx 命中率接近 100%：POI 的 {@code OpcOutputStream} 继承 {@code ZipOutputStream}，
 * 其 {@code DeflaterOutputStream} 内部压缩缓冲区也是 512 字节，与常用 bufferSize 完全撞上。
 * CSV 同样受害，只是表现为<strong>行数错乱</strong>而非「打不开」，容易被忽略——
 * 本地实测 5000 行导出，库原生实现产出 5029 行。
 * <p>
 * 本类与库的实现保持逐行一致，只改掉那处缺陷：写入侧<strong>无条件拷贝</strong>。
 * 其余细节都是必要的，实测缺一不可：
 * <ul>
 *     <li>{@code sink.complete()} 必须放在 {@code close()} 里——writer 内部会自行关闭流；
 *         改在 subscribe 的 onComplete 回调里 close+complete，会丢掉尾部数据；</li>
 *     <li>{@code subscribe} 必须用带 {@link Context} 的重载并透传 {@code sink.contextView()}；</li>
 *     <li>{@code sink.onDispose} 用于下游取消时取消上游，避免泄漏。</li>
 * </ul>
 * 验证方式见 scratchpad 的最小复现：3/134/1000/5000 行 × csv/xlsx × 512/8192 缓冲全部通过。
 *
 * @author tenant-manager
 * @since 2.11
 */
public final class ExcelExportSupport {

    /** 与 reactor-excel 单参 writeBuffer 的默认值一致 */
    private static final int BUFFER_SIZE = 8192;

    private ExcelExportSupport() {
    }

    /**
     * 生成完整文件字节。
     * <p>
     * 必须收齐再输出，不能把 {@link #writeBuffer} 的 Flux 直接交给
     * {@code response.writeWith}：实测流式写出会让内容错乱
     * （csv 行数与列数都对不上、xlsx 解压失败），而收齐后一次性输出则完全正确。
     * 导出文件本来也要求完整——xlsx 是 zip 容器，缺尾部即损坏。
     * <p>
     * 代价是整个文件驻留内存。当前订单量级（数万条约 5-10MB）可接受；
     * 若将来导出几十万条，需要改为「边生成边落临时文件、再用 zero-copy 回传」。
     */
    public static <T> Mono<byte[]> writeAll(WriterOperator<T> writer, Flux<T> data) {
        return writeBuffer(writer, data)
            .collectList()
            .map(list -> {
                int total = 0;
                for (byte[] c : list) {
                    total += c.length;
                }
                byte[] all = new byte[total];
                int pos = 0;
                for (byte[] c : list) {
                    System.arraycopy(c, 0, all, pos, c.length);
                    pos += c.length;
                }
                return all;
            });
    }

    public static <T> Flux<byte[]> writeBuffer(WriterOperator<T> writer, Flux<T> data) {
        return Flux.create(sink -> {
            OutputStream target = new OutputStream() {
                @Override
                public void write(int b) {
                    sink.next(new byte[]{(byte) b});
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    if (len > 0) {
                        // 无条件拷贝：调用方（Deflater / BufferedOutputStream）会复用 b
                        sink.next(Arrays.copyOfRange(b, off, off + len));
                    }
                }

                @Override
                public void write(byte[] b) {
                    write(b, 0, b.length);
                }
            };
            OutputStream buffered = new BufferedOutputStream(target, BUFFER_SIZE) {
                @Override
                public void close() throws IOException {
                    // super.close() 会 flush 出最后不足一块的数据，complete 必须在其之后
                    super.close();
                    sink.complete();
                }
            };
            sink.onDispose(
                writer
                    .write(data, buffered)
                    .subscribe(ignore -> {
                               },
                               sink::error,
                               () -> {
                               },
                               Context.of(sink.contextView())));
        });
    }
}
