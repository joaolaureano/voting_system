# Sem etapa de build: nao ha bundler, transpilacao nem minificacao. O arquivo que o eleitor
# abre no devtools e byte a byte o que esta no repositorio - o que importa quando a proposta
# da pagina e "nao confie no servidor, confira voce mesmo".
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY public/ /usr/share/nginx/html/

EXPOSE 8084
