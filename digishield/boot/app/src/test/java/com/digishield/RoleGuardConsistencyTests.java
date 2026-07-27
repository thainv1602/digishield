package com.digishield;

import static org.assertj.core.api.Assertions.assertThat;

import com.digishield.auth.domain.Role;
import com.digishield.shared.security.Roles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guards the role names written into {@code @PreAuthorize}.
 *
 * <p>Authorisation is expressed as string literals — {@code hasRole('ANALYST')}
 * — because a SpEL expression cannot reference a Java constant directly. Spring
 * never validates those strings: a misspelt {@code hasRole('ANALYSTS')} does not
 * fail to start, does not warn, and does not error at request time. It simply
 * matches no authority, so the endpoint quietly rejects every caller including
 * the people it was written for. Nothing else in the build would notice, which
 * is what this test is for.
 *
 * <p>Scanning is done over the classpath rather than a running context so the
 * check stays fast and covers every controller in every module.
 */
class RoleGuardConsistencyTests {

    private static final String BASE_PACKAGE = "com.digishield";

    /** {@code hasRole('X')} and {@code hasAnyRole('X','Y')}, quotes included. */
    private static final Pattern ROLE_CALL =
            Pattern.compile("hasAnyRole\\s*\\(([^)]*)\\)|hasRole\\s*\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    @Test
    void everyRoleNamedInAPreAuthorizeExists() {
        Set<String> known = Arrays.stream(Role.values()).map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> unknown = new ArrayList<>();
        for (Guard guard : findGuards()) {
            for (String role : guard.roles()) {
                if (!known.contains(role)) {
                    unknown.add(role + " in " + guard.where());
                }
            }
        }

        assertThat(unknown)
                .as("role names with no matching Role constant — these endpoints reject everyone")
                .isEmpty();
    }

    @Test
    void theScanActuallyFindsGuards() {
        // Without this, the test above passes just as happily when the scan is
        // broken and finds nothing at all — a green light meaning "no guards
        // examined" rather than "all guards are sound".
        List<Guard> guards = findGuards();

        assertThat(guards).as("controllers with @PreAuthorize").isNotEmpty();
        assertThat(guards.stream().flatMap(g -> g.roles().stream()).distinct().toList())
                .as("distinct role names found by the scan")
                .contains(Roles.SUPER_ADMIN, Roles.ORG_ADMIN);
    }

    @Test
    void sharedRoleConstantsMatchTheRoleEnum() {
        Set<String> known = Arrays.stream(Role.values()).map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (Field field : Roles.class.getDeclaredFields()) {
            if (field.getType() != String.class) {
                continue;
            }
            String value;
            try {
                value = (String) field.get(null);
            } catch (IllegalAccessException e) {
                throw new AssertionError("Roles constants must be readable", e);
            }
            assertThat(known)
                    .as("Roles.%s = '%s' has no matching Role constant", field.getName(), value)
                    .contains(value);
        }
    }

    /** Every {@code @PreAuthorize} on a controller, with the roles it names. */
    private static List<Guard> findGuards() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Guard> guards = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controller;
            try {
                controller = Class.forName(definition.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new AssertionError("Scanned class is not loadable: " + definition, e);
            }
            addGuard(guards, controller.getAnnotation(PreAuthorize.class), controller.getSimpleName());
            for (Method method : controller.getDeclaredMethods()) {
                addGuard(guards, method.getAnnotation(PreAuthorize.class),
                        controller.getSimpleName() + "#" + method.getName());
            }
        }
        return guards;
    }

    private static void addGuard(List<Guard> guards, PreAuthorize annotation, String where) {
        if (annotation == null) {
            return;
        }
        Set<String> roles = rolesIn(annotation.value());
        if (!roles.isEmpty()) {
            guards.add(new Guard(where, roles));
        }
    }

    /** Pulls the quoted role names out of any hasRole/hasAnyRole calls. */
    private static Set<String> rolesIn(String expression) {
        Set<String> roles = new LinkedHashSet<>();
        Matcher calls = ROLE_CALL.matcher(expression);
        while (calls.find()) {
            String arguments = calls.group(1) != null ? calls.group(1) : calls.group(2);
            Matcher quoted = QUOTED.matcher(arguments);
            while (quoted.find()) {
                roles.add(quoted.group(1));
            }
        }
        return roles;
    }

    private record Guard(String where, Set<String> roles) {
    }
}
