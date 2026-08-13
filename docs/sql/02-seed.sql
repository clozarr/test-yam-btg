TRUNCATE visitan, disponibilidad, inscripcion, producto, sucursal, cliente;


INSERT INTO sucursal (id, nombre, ciudad) VALUES
    (1, 'Chapinero',       'Bogota'),
    (2, 'Usaquen',         'Bogota'),
    (3, 'Kennedy',         'Bogota'),
    (4, 'Medellin Centro', 'Medellin'),
    (5, 'El Poblado',      'Medellin');


INSERT INTO producto (id, nombre, "tipoProducto") VALUES
    (1, 'Cuenta de Ahorros',     'AHORRO'),
    (2, 'Tarjeta de Credito Oro','CREDITO'),
    (3, 'CDT 360',               'INVERSION'),
    (4, 'Credito Hipotecario',   'CREDITO'),
    (5, 'Seguro de Vida',        'SEGURO');


INSERT INTO cliente (id, nombre, apellidos, ciudad) VALUES
    (1, 'Ana',    'Restrepo Gomez',  'Bogota'),
    (2, 'Bruno',  'Salazar Pena',    'Bogota'),
    (3, 'Carla',  'Duque Arango',    'Medellin'),
    (4, 'Diego',  'Marin Torres',    'Bogota'),
    (5, 'Elena',  'Ospina Valencia', 'Bogota'),
    (6, 'Felipe', 'Cardenas Ruiz',   'Bogota'),
    (7, 'Gloria', 'Nieto Amaya',     'Bogota'),
    (8, 'Hugo',   'Ramirez Lopez',   'Bogota');


INSERT INTO disponibilidad ("idSucursal", "idProducto") VALUES
    (1, 1), (2, 1), (3, 1),
    (1, 2), (2, 2), (3, 2), (4, 2), (5, 2),
    (1, 3), (2, 3),
    (4, 4);


INSERT INTO inscripcion ("idProducto", "idCliente") VALUES
    (3, 1),          -- Ana    -> CDT 360
    (1, 2),          -- Bruno  -> Cuenta de Ahorros
    (4, 3),          -- Carla  -> Hipotecario
    (2, 4),          -- Diego  -> Tarjeta Oro
    (2, 5), (3, 5),  -- Elena  -> Tarjeta Oro and CDT 360
    (5, 6),          -- Felipe -> Seguro de Vida (offered nowhere)
    (1, 7),          -- Gloria -> Cuenta de Ahorros
    (1, 8);          -- Hugo   -> Cuenta de Ahorros


INSERT INTO visitan ("idSucursal", "idCliente", "fechaVisita") VALUES
    (1, 1, DATE '2026-03-02'), (2, 1, DATE '2026-03-09'), (3, 1, DATE '2026-04-01'),  -- Ana  {1,2,3}
    (1, 2, DATE '2026-03-04'), (2, 2, DATE '2026-03-18'),                             -- Bruno{1,2}
    (4, 3, DATE '2026-02-11'), (5, 3, DATE '2026-02-25'),                             -- Carla{4,5}
    (1, 4, DATE '2026-01-15'), (2, 4, DATE '2026-01-22'),
    (3, 4, DATE '2026-02-05'), (4, 4, DATE '2026-02-19'),                             -- Diego{1,2,3,4}
    (1, 5, DATE '2026-03-11'), (2, 5, DATE '2026-03-12'),
    (3, 5, DATE '2026-03-13'), (4, 5, DATE '2026-03-14'),                             -- Elena{1,2,3,4}
    (1, 6, DATE '2026-04-07'),                                                        -- Felipe{1}
    (1, 8, DATE '2026-04-08'), (2, 8, DATE '2026-04-09'),
    (3, 8, DATE '2026-04-10');                                                        -- Hugo {1,2,3}
