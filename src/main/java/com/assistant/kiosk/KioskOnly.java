package com.assistant.kiosk;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Reserve l'endpoint au navigateur du Pi : l'en-tete {@code X-Kiosk-Key} doit porter la cle configuree. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KioskOnly {
}
