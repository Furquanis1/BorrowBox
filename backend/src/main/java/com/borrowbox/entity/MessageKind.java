package com.borrowbox.entity;

/**
 * Conversation message kinds (V2.2.3).
 *
 * USER messages are written by a transaction participant through the public
 * message API. SYSTEM messages are server-generated timeline entries
 * (handover scheduled, loan started, return initiated, loan completed) with a
 * null author; they cannot be created through the public API.
 */
public enum MessageKind {
    USER,
    SYSTEM
}