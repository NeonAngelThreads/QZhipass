package org.microsoft.qintelipass;

import org.microsoft.qintelipass.models.User;

public interface ILoginable {
    User loginByNameAndPassword(String username, String password);
    User loginByPhoneAndPassword(String phoneNumber, String password);
    User loginByEmailAndPassword(String email, String password);
}
