package lugares.service;


import lugares.Domains.ApiException;
import lugares.Domains.Lugar;
import lugares.api.Dtos;
import lugares.repository.LugaresRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LugaresService {
    private final LugaresRepository repository;

    public LugaresService(LugaresRepository repository) {
        this.repository = repository;
    }

    public Long crearLugar(Dtos.CrearLugarRequest req){
        if (req.nombre() == null || req.nombre().isBlank() ||
                req.direccion() == null || req.direccion().isBlank() ||
                req.ciudad() == null || req.ciudad().isBlank() ||
                req.latitud() == null || req.longitud() == null) {
            throw ApiException.badRequest("DATOS_INVALIDOS",
                    "Todos los campos son obligatorios: nombre, direccion, ciudad, latitud y longitud");
        }
        return repository.guardarLugar(req.nombre(),req.direccion(),req.latitud(),req.longitud(),req.ciudad());
    }

    public Dtos.LugarView obtenerLugarPorId(Long id){
        Optional<Lugar> opt = repository.buscarLugarPorId(id);
        if(opt.isEmpty()){
            throw ApiException.notFound("LUGAR_NO_ENCONTRADO","No existe el lugar con id "+id);
        }
        Lugar l = opt.get();
        return new Dtos.LugarView(l.id(),l.nombre(),l.direccion(),l.latitud(),l.longitud(),l.ciudad());
    }
}
