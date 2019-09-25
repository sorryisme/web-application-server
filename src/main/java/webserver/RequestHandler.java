package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import db.DataBase;
import model.User;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (
                InputStream in = connection.getInputStream(); 
                OutputStream out = connection.getOutputStream();
                ) {
            //requset 저장
            BufferedReader br = new BufferedReader(new InputStreamReader(in,"UTF-8"));
            String reqContents = br.readLine();
            log.info("req line :{}" , reqContents);
            if(reqContents == null) {
                return;
            }
            
            String[] tokens =  reqContents.split(" ");
            boolean logined =false;
            int contentLength = 0;
            while(!reqContents.equals("")) {
                log.debug("header : {}" , reqContents);
                reqContents = br.readLine();
                if(reqContents .contains("Content-Length")) {
                    contentLength = getContentLength(reqContents);
                }
                if(reqContents.contains("Cookie")) {
                    logined = isLogin(reqContents);
                }
                // 길이저장
            }
            
            String url = tokens[1];
            
            if("/user/create".equals(url)) {
                String body = IOUtils.readData(br, contentLength); 
                //user/create로 들어왔을 때  body를 읽어온다
                Map<String, String> params = 
                        HttpRequestUtils.parseQueryString(body);
                //& 연산자로 묶인 것들을 처리
                
                User user = new User(params.get("userId"), params.get("password"), params.get("name"),params.get("email"));
                //map에 저장된 값처리
                log.debug("User : {}", user);
                DataBase.addUser(user);
                // database 대체
                log.debug("DataBase Save Check : {} " , DataBase.findUserById(user.getUserId()));
            } else if("/user/login".equals(url)) {
                String body =IOUtils.readData(br, contentLength);
                log.debug("/user/login - body : {}", body);
                Map<String, String> params = HttpRequestUtils.parseQueryString(body);
                User user = DataBase.findUserById(params.get("userId"));
                if(user == null) {
                    // 로그인 유저아이디 값이 
                    responseResource(out, "/user/login_failed.html");
                    return;
                } else if("/user/list".equals(url)) {
                    if(!logined) {
                        responseResource(out, "/user/login.html");
                        return;
                    } 
                    
                    Collection<User> users = DataBase.findAll();
                    StringBuilder sb = new StringBuilder();
                    sb.append("<table border='1'>");
                    for(User userUnit : users) {
                        sb.append("<tr>");
                        sb.append("<td>"+userUnit.getUserId()+"</td>");
                        sb.append("<td>"+userUnit.getName() +"</td>");
                        sb.append("<td>"+userUnit.getEmail()+"</td>");
                        sb.append("</tr>");
                    }
                    
                    sb.append("</table>");
                    byte[] listBody = sb.toString().getBytes();
                    DataOutputStream dos = new DataOutputStream(out);
                    response200Header(dos, body.length());
                    responseBody(dos, listBody);
                }
                //비밀번호가 맞다면 
                if(user.getPassword().equals(params.get("password"))){
                    //Login 성공처리
                    DataOutputStream dos = new DataOutputStream(out);
                    response302LoginSuccessHeader(dos);
                } else {
                    // 실패시 로그인 실패처리
                    responseResource(out, "/user/login_failed.html");
                }
                
            } else {
                responseResource(out, url);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
    
    private void responseResource(OutputStream out, String url) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        byte[] body  = Files.readAllBytes(new File("./webapp" + url).toPath());
        response200Header(dos, body.length);
        //성공 헤더 작성 전달
        responseBody(dos,body);
        //결과 body 전달
    }

    private void response302LoginSuccessHeader(DataOutputStream dos) {
        try {
            // 성공시 setCookie 처리
            dos.writeBytes("HTTP/1.1 302 redirect \r\n");
            dos.writeBytes("Set-Cookie: logined=true \r\n");
            dos.writeBytes("Location: /index.html \r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
    
    private int getContentLength(String line) {
        String[] headerTokens = line.split(":");
        return Integer.parseInt(headerTokens[1].trim());
    }
    
    private boolean isLogin(String line) {
        String[] headerTokens = line.split(":");
        Map<String, String> cookies = 
                HttpRequestUtils.parseCookies(headerTokens[1].trim());
        String value = cookies.get("logined");
        if(value == null) {
            return false;
        }
        return Boolean.parseBoolean(value);
    }
    
    private void response302Header(DataOutputStream dos, String url) {
        try {
            dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
            dos.writeBytes("Location: " + url + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
