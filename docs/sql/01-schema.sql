DROP TABLE IF EXISTS visitan;
DROP TABLE IF EXISTS disponibilidad;
DROP TABLE IF EXISTS inscripcion;
DROP TABLE IF EXISTS producto;
DROP TABLE IF EXISTS sucursal;
DROP TABLE IF EXISTS cliente;

CREATE TABLE cliente (
    id        INTEGER      NOT NULL,
    nombre    VARCHAR(80)  NOT NULL,
    apellidos VARCHAR(120) NOT NULL,
    ciudad    VARCHAR(80)  NOT NULL,
    CONSTRAINT pk_cliente PRIMARY KEY (id)
);

CREATE TABLE producto (
    id           INTEGER     NOT NULL,
    nombre       VARCHAR(80) NOT NULL,
    "tipoProducto" VARCHAR(40) NOT NULL,
    CONSTRAINT pk_producto PRIMARY KEY (id)
);

CREATE TABLE sucursal (
    id     INTEGER     NOT NULL,
    nombre VARCHAR(80) NOT NULL,
    ciudad VARCHAR(80) NOT NULL,
    CONSTRAINT pk_sucursal PRIMARY KEY (id)
);


CREATE TABLE inscripcion (
    "idProducto" INTEGER NOT NULL,
    "idCliente"  INTEGER NOT NULL,
    CONSTRAINT pk_inscripcion PRIMARY KEY ("idProducto", "idCliente"),
    CONSTRAINT fk_inscripcion_producto FOREIGN KEY ("idProducto") REFERENCES producto (id),
    CONSTRAINT fk_inscripcion_cliente  FOREIGN KEY ("idCliente")  REFERENCES cliente (id)
);


CREATE TABLE disponibilidad (
    "idSucursal" INTEGER NOT NULL,
    "idProducto" INTEGER NOT NULL,
    CONSTRAINT pk_disponibilidad PRIMARY KEY ("idSucursal", "idProducto"),
    CONSTRAINT fk_disponibilidad_sucursal FOREIGN KEY ("idSucursal") REFERENCES sucursal (id),
    CONSTRAINT fk_disponibilidad_producto FOREIGN KEY ("idProducto") REFERENCES producto (id)
);


CREATE TABLE visitan (
    "idSucursal"  INTEGER NOT NULL,
    "idCliente"   INTEGER NOT NULL,
    "fechaVisita" DATE    NOT NULL,
    CONSTRAINT pk_visitan PRIMARY KEY ("idSucursal", "idCliente"),
    CONSTRAINT fk_visitan_sucursal FOREIGN KEY ("idSucursal") REFERENCES sucursal (id),
    CONSTRAINT fk_visitan_cliente  FOREIGN KEY ("idCliente")  REFERENCES cliente (id)
);


CREATE INDEX ix_inscripcion_cliente    ON inscripcion ("idCliente");
CREATE INDEX ix_disponibilidad_producto ON disponibilidad ("idProducto");
CREATE INDEX ix_visitan_cliente        ON visitan ("idCliente");
