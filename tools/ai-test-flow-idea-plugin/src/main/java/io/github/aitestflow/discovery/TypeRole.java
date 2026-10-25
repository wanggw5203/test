package io.github.aitestflow.discovery;

/** Role played by a Java type in the discovered interface contract. */
public enum TypeRole {
    REQUEST,
    RESPONSE,
    BO,
    VO,
    DTO,
    ENTITY,
    ENUM,
    OTHER
}
