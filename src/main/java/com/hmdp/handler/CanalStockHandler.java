package com.hmdp.handler;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetSocketAddress;
import java.util.List;

@Slf4j
public class CanalStockHandler {

    @Setter
    private InetSocketAddress canalServer;

    @Setter
    private String destination;

    @Setter
    private String subscribeFilter;

    @Setter
    private boolean enabled = true;

    private StringRedisTemplate stringRedisTemplate;
    private CanalConnector connector;
    private volatile boolean running = true;

    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void start() {
        if (!enabled) {
            log.info("Canal client is disabled");
            return;
        }

        Thread worker = new Thread(this::consumeLoop, "canal-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void consumeLoop() {
        while (running) {
            long batchId = -1L;
            try {
                ensureConnected();
                Message message = connector.getWithoutAck(1000);
                batchId = message.getId();
                if (batchId == -1 || message.getEntries().isEmpty()) {
                    sleep(200);
                    continue;
                }

                handleEntries(message.getEntries());
                connector.ack(batchId);
            } catch (Exception e) {
                log.error("Canal consume failed, batchId={}", batchId, e);
                rollbackQuietly(batchId);
                reconnectQuietly();
                sleep(1000);
            }
        }
    }

    private void ensureConnected() {
        if (connector != null) {
            return;
        }
        connector = CanalConnectors.newSingleConnector(canalServer, destination, "", "");
        connector.connect();
        connector.subscribe(subscribeFilter);
        connector.rollback();
        log.info("Canal client connected, destination={}, filter={}", destination, subscribeFilter);
    }

    private void handleEntries(List<CanalEntry.Entry> entries) {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN
                    || entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                continue;
            }

            CanalEntry.RowChange rowChange;
            try {
                rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                log.error("Failed to parse Canal row change", e);
                continue;
            }

            String tableName = entry.getHeader().getTableName();
            CanalEntry.EventType eventType = rowChange.getEventType();

            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                if ("tb_seckill_voucher".equals(tableName)
                        && (eventType == CanalEntry.EventType.UPDATE || eventType == CanalEntry.EventType.INSERT)) {
                    syncStock(rowData);
                } else if ("tb_voucher_order".equals(tableName)
                        && eventType == CanalEntry.EventType.INSERT) {
                    syncOrder(rowData);
                }
            }
        }
    }

    private void syncStock(CanalEntry.RowData rowData) {
        String voucherId = valueOf(rowData, "voucher_id");
        String stock = valueOf(rowData, "stock");
        if (voucherId == null || stock == null || stringRedisTemplate == null) {
            return;
        }

        String redisKey = "seckill:stock:" + voucherId;
        stringRedisTemplate.opsForValue().set(redisKey, stock);
        log.debug("Canal synced seckill stock to Redis, voucherId={}, stock={}", voucherId, stock);
    }

    private void syncOrder(CanalEntry.RowData rowData) {
        String userId = valueOf(rowData, "user_id");
        String voucherId = valueOf(rowData, "voucher_id");
        if (userId == null || voucherId == null || stringRedisTemplate == null) {
            return;
        }

        String orderKey = "seckill:order:" + voucherId;
        stringRedisTemplate.opsForSet().add(orderKey, userId);
        log.debug("Canal synced seckill order to Redis, voucherId={}, userId={}", voucherId, userId);
    }

    private String valueOf(CanalEntry.RowData rowData, String columnName) {
        for (CanalEntry.Column column : rowData.getAfterColumnsList()) {
            if (columnName.equals(column.getName())) {
                return column.getValue();
            }
        }
        return null;
    }

    private void rollbackQuietly(long batchId) {
        if (connector == null) {
            return;
        }
        try {
            if (batchId > 0) {
                connector.rollback(batchId);
            } else {
                connector.rollback();
            }
        } catch (Exception ignored) {
            log.warn("Canal rollback failed and will reconnect");
        }
    }

    private void reconnectQuietly() {
        if (connector != null) {
            try {
                connector.disconnect();
            } catch (Exception ignored) {
                log.warn("Canal disconnect failed during reconnect");
            }
            connector = null;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        running = false;
        reconnectQuietly();
        log.info("Canal client stopped");
    }
}
