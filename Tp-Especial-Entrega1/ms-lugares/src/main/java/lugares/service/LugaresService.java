package lugares.service;


import lugares.Domains.ApiException;
import lugares.Domains.Asiento;
import lugares.Domains.Lugar;
import lugares.Domains.Sector;
import lugares.api.Dtos;
import lugares.repository.LugaresRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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

    @Transactional
    public Long crearSector(Long lugarId,Dtos.CrearSectorRequest req){
        if(repository.buscarLugarPorId(lugarId).isEmpty()){
            throw ApiException.notFound("LUGAR_NO_ENCONTRADO","No existe el lugar con id "+lugarId);
        }
        if(req.nombre()==null||req.nombre().isBlank()){
            throw ApiException.badRequest("DATOS_INVALIDOS",
                    "Los campos ingresados son invalidos");
        }
        if (!req.numerado()) {
            if (req.capacidad() == null || req.capacidad() <= 0) {
                throw ApiException.badRequest("DATOS_INVALIDOS", "La capacidad debe ser mayor a cero");
            }
        } else {
            if (req.totalFilas() == null || req.totalFilas() <= 0 ||
                    req.asientosPorFila() == null || req.asientosPorFila() <= 0) {
                throw ApiException.badRequest("DATOS_INVALIDOS", "totalFilas y asientosPorFila deben ser mayores a cero");
            }
        }
        int capacidad;
        if(req.numerado()){
            capacidad= req.asientosPorFila()* req.totalFilas();
        }else{
            capacidad=req.capacidad();
        }
        Long idSector = repository.guardarSector(lugarId,req.nombre(),req.numerado(),capacidad);
        if(req.numerado()){
            List<Asiento> asientos= new ArrayList<>();
            for(int f =1;f<=req.totalFilas();f++){
                for(int a =1;a<=req.asientosPorFila();a++){
                    asientos.add(new Asiento(null,idSector,String.valueOf(f),String.valueOf(a)));
                }
            }
            repository.guardarAsientosBatch(asientos);
        }

        return idSector;
    }

    public List<Dtos.SectorView> listarSectoresPorLugar(Long lugarId) {
        if (repository.buscarLugarPorId(lugarId).isEmpty()) {
            throw ApiException.notFound("LUGAR_NO_ENCONTRADO", "No existe el lugar con id " + lugarId);
        }

        return repository.listarSectoresPorLugar(lugarId).stream()
                .map(s -> new Dtos.SectorView(s.id(), s.lugarId(), s.nombre(), s.numerado(), s.capacidad()))
                .toList();
    }
}
