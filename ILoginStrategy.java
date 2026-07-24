package org.microsoft.qintelipass;


import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.response.ResponseBody;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public interface ILoginStrategy {
    String getType();
    ResponseBody<User> authenticate(Map<String, Object> params);
}
