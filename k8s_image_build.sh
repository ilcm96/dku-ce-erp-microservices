docker build -t api-gateway:latest -f api-gateway/Dockerfile .
docker build -t employee-service:latest -f employee-service/Dockerfile .
docker build -t approval-request-service:latest -f approval-request-service/Dockerfile .
docker build -t approval-processing-service:latest -f approval-processing-service/Dockerfile .
docker build -t notification-service:latest -f notification-service/Dockerfile .
