package com.cmclinnovations.agent.config;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;

import com.cmclinnovations.agent.utils.StringResource;

@ControllerAdvice
public class GlobalHeaderAdvice {

    @ModelAttribute
    public void addGlobalRolesHeader(
            @RequestHeader(name = "X-User-Roles", required = false) String roles, Model model) {
        model.addAttribute(StringResource.HEADER_ROLES, (roles == null) ? "" : roles);
    }
}