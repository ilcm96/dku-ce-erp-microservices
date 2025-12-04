package erp.approvalrequest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"erp.approvalrequest", "erp.common"})
public class ApprovalRequestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalRequestServiceApplication.class, args);
    }
}
