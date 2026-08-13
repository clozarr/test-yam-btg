SELECT DISTINCT c.nombre
FROM cliente c
JOIN inscripcion i
  ON i."idCliente" = c.id
WHERE EXISTS (
        SELECT 1
        FROM disponibilidad d
        WHERE d."idProducto" = i."idProducto"
      )
  AND NOT EXISTS (
        SELECT 1
        FROM disponibilidad d
        WHERE d."idProducto" = i."idProducto"
          AND NOT EXISTS (
                SELECT 1
                FROM visitan v
                WHERE v."idSucursal" = d."idSucursal"
                  AND v."idCliente"  = c.id
              )
      )
ORDER BY c.nombre;
