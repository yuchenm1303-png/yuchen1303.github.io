package com.yuchen.ailedger.ui.gl

/** Current body refraction; constants and operation order intentionally match the tuned web version. */
internal object WebOpenGLGlassBodyShader {
    const val SOURCE = """
        float centerEnvelope(vec2 u){
            float width=sat((uBody.x-0.18)/(1.5-0.18));
            vec2 span=vec2(mix(0.64,0.99,width),mix(0.56,0.90,width));
            vec2 q=abs(u)/max(span,vec2(0.001));
            return exp(-(pow(q.x,4.0)+pow(q.y,4.0)));
        }
        vec2 polynomialTransport(vec2 u){
            float curve=sat((uBody.y-0.2)/3.0);
            float ky=mix(0.14,0.48,curve);
            float kx=mix(0.10,0.42,curve);
            float ay=mix(0.44,0.76,curve);
            float yRelax=mix(0.24,0.42,curve);
            float xBoost=mix(0.08,0.20,curve);
            vec2 transport=vec2(
                u.x*(1.0-ky*u.y*u.y),
                -ay*u.y*(1.0-kx*u.x*u.x)
            );
            transport.x+=u.x*xBoost*(1.0-0.65*u.y*u.y);
            transport.y+=u.y*yRelax*(1.0-0.75*u.x*u.x);
            vec2 tangent=vec2(-u.y,u.x);
            transport+=tangent*mix(0.006,0.026,curve);
            return transport;
        }
        float centerLimitPx(vec2 z){
            float width=sat((uBody.x-0.18)/(1.5-0.18));
            float gain=sat(uBody.z/900.0);
            return mix(38.0,86.0,width)*mix(0.55,1.0,gain);
        }
        float ringBandWidthPx(vec2 z){
            return max(uBodyBand.y*min(z.x,z.y)*0.18,1.0);
        }
        float ringBandCenterPx(vec2 z,float width){
            float halfMin=min(z.x,z.y)*0.5;
            float raw=(1.0-uBodyBand.x)*halfMin;
            return clamp(raw,0.0,max(uFlowDepth-width*1.20,0.0));
        }
        float ringShell(float depth,vec2 z){
            float width=ringBandWidthPx(z);
            float center=ringBandCenterPx(z,width);
            return gauss(depth,center,width)+gauss(depth,center+width*0.42,width*1.75)*0.78;
        }
        float ringSafeLimit(vec2 z,float ringSafe){
            float width=ringBandWidthPx(z);
            return max(4.0,min(width*4.20,uFlowDepth*0.46))*pow(sat(ringSafe),0.64);
        }
    """
}
