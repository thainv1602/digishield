package com.digishield.shared.persistence;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Marks demo seeding so RLS is bypassed for the seeders.
 * <p>
 * The {@code *Seeder} beans create data across several tenants and run at startup
 * with no request context. Under a non-{@code dev} profile the app enforces RLS
 * ({@link RlsTenantAspect} sets the {@code app.tenant_id} GUC and drops to a
 * non-superuser role per transaction), which would reject cross-tenant seed
 * inserts. This aspect wraps each seeder's {@code run(..)} in a
 * {@link SeedingContext} window so those repository calls run as the connected
 * superuser (RLS bypassed). Active under {@code dev} (harmless — RLS is off) and
 * {@code seed} (where it is required). Ordered outside {@link RlsTenantAspect},
 * which fires on the inner repository calls.
 */
@Aspect
@Component
@Profile("dev | seed")
@Order(0)
public class SeedTenantContextAspect {

    @Around("execution(* com.digishield..*Seeder.run(..))")
    public Object asSeeding(ProceedingJoinPoint pjp) throws Throwable {
        boolean nested = SeedingContext.isSeeding();
        SeedingContext.begin();
        try {
            return pjp.proceed();
        } finally {
            if (!nested) {
                SeedingContext.end();
            }
        }
    }
}
