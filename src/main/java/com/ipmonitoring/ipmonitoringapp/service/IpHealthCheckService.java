package com.ipmonitoring.ipmonitoringapp.service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ipmonitoring.ipmonitoringapp.model.IpAddress;
import com.ipmonitoring.ipmonitoringapp.model.IpStatusHistory;
import com.ipmonitoring.ipmonitoringapp.repository.IpAddressRepository;
import com.ipmonitoring.ipmonitoringapp.repository.IpStatusHistoryRepository;

@Service
public class IpHealthCheckService {

    private static final int MAX_HISTORY_SIZE = 30; // 30 pings = ~1 minute at 2s interval
    private static final int DEGRADED_DOWN_COUNT_THRESHOLD = 10;

    private final IpAddressRepository repository;
    private final IpStatusHistoryRepository historyRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    private ConcurrentHashMap<Long, Deque<Boolean>> ipStatusHistoryMap = new ConcurrentHashMap<>();

    public IpHealthCheckService(IpAddressRepository repository, IpStatusHistoryRepository historyRepository) {
        this.repository = repository;
        this.historyRepository = historyRepository;
    }

    @Scheduled(fixedDelay = 2000)
    public void healthCheckAllIps() {
        List<IpAddress> ips = repository.findAll();
        for (IpAddress ipAddress : ips) {
            executor.submit(() -> checkAndUpdateStatus(ipAddress));
        }
    }

    private void checkAndUpdateStatus(IpAddress ipAddress) {
        Long ipId = ipAddress.getId();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

        boolean isDown;
        try {
            InetAddress inet = InetAddress.getByName(ipAddress.getIp());
            boolean reachable = inet.isReachable(2000);
            isDown = !reachable;
        } catch (Exception e) {
            isDown = true;
        }

        Deque<Boolean> historyDeque = ipStatusHistoryMap.computeIfAbsent(ipId, k -> new ConcurrentLinkedDeque<>());

        synchronized (historyDeque) {
            if (historyDeque.size() == MAX_HISTORY_SIZE) {
                historyDeque.pollFirst();
            }
            historyDeque.addLast(isDown);
        }

        int downCount;
        synchronized (historyDeque) {
            downCount = (int) historyDeque.stream().filter(status -> status).count();
        }

        String status;
        if (downCount == MAX_HISTORY_SIZE) {
            status = "Down"; // continuously down all last 30 pings
        } else if (downCount >= DEGRADED_DOWN_COUNT_THRESHOLD) {
            status = "Degraded"; // fluctuating with many downs
        } else {
            status = "Online"; // mostly stable online
        }

        String prevStatus = ipAddress.getStatus();
        ipAddress.setLastChecked(now);

        if (prevStatus == null || !status.equals(prevStatus)) {
            ipAddress.setStatusChangeCount(ipAddress.getStatusChangeCount() + 1);
            ipAddress.setLastStatusChangeStart(
                    ipAddress.getLastStatusChangeEnd() != null ? ipAddress.getLastStatusChangeEnd() : now);
            ipAddress.setLastStatusChangeEnd(now);
            ipAddress.setStatus(status);
        } else {
            ipAddress.setStatus(status);
        }

        repository.save(ipAddress);

        IpStatusHistory history = new IpStatusHistory();
        history.setIpId(ipAddress.getId());
        history.setLocation(ipAddress.getLocation());
        history.setIp(ipAddress.getIp());
        history.setStatus(status);
        history.setCheckedAt(now);
        history.setStatusChangeCount(ipAddress.getStatusChangeCount());
        historyRepository.save(history);
    }
}
