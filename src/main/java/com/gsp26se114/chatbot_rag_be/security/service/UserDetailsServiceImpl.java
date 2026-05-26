package com.gsp26se114.chatbot_rag_be.security.service;

import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.repository.RoleRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return com.gsp26se114.chatbot_rag_be.config.TenantContext.withDefaultDataSource(() -> {
            // 1. Find user in the Main DB by email.
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User Not Found with email: " + email));

            // 2. Check whether the account is disabled.
            if (Boolean.FALSE.equals(user.getIsActive())) {
                throw new DisabledException("Account has been disabled: " + email);
            }

            // 3. Load role information from the Main DB.
            RoleEntity role = roleRepository.findById(user.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Role not found with id: " + user.getRoleId()));

            // 4. Return UserPrincipal with role information.
            return UserPrincipal.build(user, role);
        });
    }
}
