package org.microsoft.qintelipass;

import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.request.RegisterRequest;

public interface IRegisterable {
    User register(RegisterRequest request, String password);
}
