FROM alpine:3.15.1 as stage1
RUN echo "stage1" >> /stage-name

FROM alpine:3.15.1
RUN echo "main-stage" >> /stage-name
RUN echo "This stage should never be executed!" && exit 1
