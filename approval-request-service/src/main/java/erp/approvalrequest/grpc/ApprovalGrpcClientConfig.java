package erp.approvalrequest.grpc;

import erp.shared.proto.approval.ApprovalGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * Approval Processing 서비스용 gRPC 클라이언트 설정.
 * 채널 이름은 application.yml 의 spring.grpc.client.channels.approval-processing 를 사용합니다.
 */
@Configuration
public class ApprovalGrpcClientConfig {

    private static final String CHANNEL_NAME = "approval-processing";

    @Bean
    public ApprovalGrpc.ApprovalBlockingStub approvalBlockingStub(GrpcChannelFactory channelFactory) {
        return ApprovalGrpc.newBlockingStub(channelFactory.createChannel(CHANNEL_NAME));
    }
}
