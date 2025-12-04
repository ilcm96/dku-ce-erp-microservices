package erp.approvalprocessing.service;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.LinkedList;

import org.springframework.stereotype.Service;

import erp.shared.proto.approval.ApprovalRequest;
import erp.shared.proto.approval.Step;
import erp.shared.proto.approval.StepStatus;

@Service
public class ApprovalQueueService {

    private final Map<Long, List<ApprovalRequest>> queueByApprover = new ConcurrentHashMap<>();

    public void enqueue(ApprovalRequest request) {
        Step next = request.getStepsList().stream()
                .filter(step -> step.getStatus() == StepStatus.STEP_STATUS_PENDING)
                .min(Comparator.comparingInt(Step::getStep))
                .orElse(null);

        if (next == null) {
            return;
        }

        List<ApprovalRequest> queue = queueByApprover.computeIfAbsent(
                next.getApproverId(), k -> Collections.synchronizedList(new LinkedList<>()));

        synchronized (queue) {
            queue.removeIf(req -> req.getRequestId() == request.getRequestId());
            int insertIdx = 0;
            while (insertIdx < queue.size()) {
                ApprovalRequest existing = queue.get(insertIdx);
                if (comparePriority(request, existing) <= 0) {
                    break;
                }
                insertIdx++;
            }
            queue.add(insertIdx, request);
        }
    }

    public List<ApprovalRequest> getQueue(Long approverId) {
        List<ApprovalRequest> queue = queueByApprover.getOrDefault(approverId, List.of());
        return List.copyOf(queue);
    }

    public ApprovalRequest remove(Long approverId, Long requestId) {
        List<ApprovalRequest> queue = queueByApprover.get(approverId);
        if (queue == null) {
            return null;
        }

        synchronized (queue) {
            Iterator<ApprovalRequest> iterator = queue.iterator();
            while (iterator.hasNext()) {
                ApprovalRequest req = iterator.next();
                if (req.getRequestId() == requestId) {
                    iterator.remove();
                    return req;
                }
            }
        }
        return null;
    }

    private int comparePriority(ApprovalRequest a, ApprovalRequest b) {
        // 우선순위: step 번호 오름차순 -> requestId 오름차순
        int stepA = a.getStepsList().stream()
                .filter(s -> s.getStatus() == StepStatus.STEP_STATUS_PENDING)
                .min(Comparator.comparingInt(Step::getStep))
                .map(Step::getStep)
                .orElse(Integer.MAX_VALUE);
        int stepB = b.getStepsList().stream()
                .filter(s -> s.getStatus() == StepStatus.STEP_STATUS_PENDING)
                .min(Comparator.comparingInt(Step::getStep))
                .map(Step::getStep)
                .orElse(Integer.MAX_VALUE);

        if (stepA != stepB) {
            return Integer.compare(stepA, stepB);
        }
        return Long.compare(a.getRequestId(), b.getRequestId());
    }
}
