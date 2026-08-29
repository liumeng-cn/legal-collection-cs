# 国内镜像源加速：基础镜像走 1ms.run，apt 走 aliyun，github 源码走 gh-proxy（均无需账号密码）
FROM docker.1ms.run/pgvector/pgvector:pg16

# 编译安装 zhparser（SCWS 中文分词），为中文关键词召回提供 tsvector
RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g' \
        /etc/apt/sources.list /etc/apt/sources.list.d/debian.sources 2>/dev/null || true \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential postgresql-server-dev-16 git wget bzip2 ca-certificates \
    && git config --global url."https://gh-proxy.com/https://github.com/".insteadOf "https://github.com/" \
    && cd /tmp \
    && wget -q -O scws.tar.bz2 http://www.xunsearch.com/scws/down/scws-1.2.3.tar.bz2 \
    && tar xf scws.tar.bz2 \
    && cd scws-1.2.3 \
    && ./configure --prefix=/usr/local \
    && make \
    && make install \
    && ldconfig \
    && cd /tmp \
    && git clone --depth 1 https://github.com/amutu/zhparser.git \
    && cd zhparser \
    && make \
    && make install \
    && cd / \
    && rm -rf /tmp/scws* /tmp/zhparser \
    && apt-get purge -y --auto-remove build-essential postgresql-server-dev-16 git wget bzip2 \
    && rm -rf /var/lib/apt/lists/*
