package box2dLight;

public class LightData {
	
	float height;
	
	int shadowsDropped = 0;
	
	public LightData(float h) {
		height = h;
	}
	
	public float getLimit(float distance, float lightHeight, float lightRange) {
		float l = 0f;
		if (lightHeight > height) {
			l = lightRange * height / (lightHeight - height);
			float diff = lightRange - distance;
			if (l > diff) l = diff;
		} else if (lightHeight == 0f) {
			l = lightRange;
		} else {
			l = lightRange - distance;
		}
		if (l < 0) l = 0f;
		return l;
	}
	
}
