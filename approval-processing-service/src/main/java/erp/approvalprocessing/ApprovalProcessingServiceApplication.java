package erp.approvalprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"erp.approvalprocessing", "erp.common"})
public class ApprovalProcessingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalProcessingServiceApplication.class, args);
    }
}
