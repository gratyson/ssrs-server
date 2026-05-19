FROM amazoncorretto:21-al2023
WORKDIR /app
COPY build/libs/ssrs-1.0.jar /app/ssrs-1.0.jar
EXPOSE 8443
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SPRING_OPTS="-Dspring.profiles.active=dev -Dserver.servlet.context-path=/ssrs/"
CMD ["sh", "-c", "exec java -jar -Dspring.profiles.active=dev -Dserver.servlet.context-path=/ssrs/ /app/ssrs-1.0.jar"]