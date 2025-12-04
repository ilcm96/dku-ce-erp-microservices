package erp.approvalprocessing.grpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

import erp.shared.proto.approval.ApprovalGrpc;

@Configuration
public class ApprovalRequestGrpcClientConfig {

    private static final String CHANNEL_NAME = "approval-request";

    @Bean
    public ApprovalGrpc.ApprovalBlockingStub approvalRequestStub(GrpcChannelFactory channelFactory) {
        return ApprovalGrpc.newBlockingStub(channelFactory.createChannel(CHANNEL_NAME));
    }
}
