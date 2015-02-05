package box2dLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Mesh.VertexDataType;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/**
 * Light shaped as a circle with given radius
 * 
 * <p>Extends {@link PositionalLight}
 * 
 * @author kalle_h
 */
public class PointLight extends PositionalLight {

	/**
	 * Creates light shaped as a circle with default radius (15f), color and
	 * position (0f, 0f)
	 * 
	 * @param rayHandler
	 *            not {@code null} instance of RayHandler
	 * @param rays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 */
	public PointLight(RayHandler rayHandler, int rays) {
		this(rayHandler, rays, Light.DefaultColor, 15f, 0f, 0f);
	}
	
	/**
	 * Creates light shaped as a circle with given radius
	 * 
	 * @param rayHandler
	 *            not {@code null} instance of RayHandler
	 * @param rays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 * @param color
	 *            color, set to {@code null} to use the default color
	 * @param distance
	 *            distance of light
	 * @param x
	 *            horizontal position in world coordinates
	 * @param y
	 *            vertical position in world coordinates
	 */
	public PointLight(RayHandler rayHandler, int rays, Color color,
			float distance, float x, float y) {
		super(rayHandler, rays, color, distance, x, y, 0f);
	}
	
	@Override
	public void update () {
		updateBody();
		if (dirty) setEndPoints();
		
		if (cull()) return;
		if (staticLight && !dirty) return;
		
		dirty = false;
		updateMesh();
		
		if (rayHandler.pseudo3d) {
			prepeareFixtureData();
		}
	}
	
	@Override
	void dynamicShadowRender () {
		updateDynamicShadowMeshes();
		for (Mesh m : dynamicShadowMeshes) {
			m.render(rayHandler.lightShader, GL20.GL_TRIANGLE_STRIP);
		}
	}
	
	final Vector2 center = new Vector2(); 
	final IntArray ind = new IntArray();
	final LightData tmpData = new LightData(0f);
	final Array<Vector2> tmpVerts = new Array<Vector2>();
	
	protected void updateDynamicShadowMeshes() {
		for (Mesh mesh : dynamicShadowMeshes) {
			mesh.dispose();
		}
		dynamicShadowMeshes.clear();
		
		if (dynamicSegments == null) {
			dynamicSegments = new float[vertexNum * 16];
		}
		
		float colBits = rayHandler.ambientLight.toFloatBits();
		for (Fixture fixture : affectedFixtures) {
			LightData data = (LightData)fixture.getUserData();
			if (data == null) continue;
			
			Shape fixtureShape = fixture.getShape();
			center.set(fixture.getBody().getWorldCenter());
			float l = 0f;
			float f = 1f / data.shadowsDropped;
			if (fixtureShape instanceof PolygonShape) {
				PolygonShape shape = (PolygonShape)fixtureShape;
				int size = 0;
				int minN = -1;
				int maxN = -1;
				int minDstN = -1;
				float minDst = Float.POSITIVE_INFINITY;
				boolean hasGasp = false;
				tmpVerts.clear();
				for (int n = 0; n < shape.getVertexCount(); n++) {
					shape.getVertex(n, tmpVec);
					tmpVec.set(fixture.getBody().getWorldPoint(tmpVec));
					tmpVerts.add(tmpVec.cpy());
					tmpEnd.set(tmpVec).sub(start).limit(0.01f).add(tmpVec);
					if (fixture.testPoint(tmpEnd)) {
						if (n > minN) minN = n;
						maxN = n;
						hasGasp = true;
						continue;
					}
					float currDist = tmpVec.dst2(start);
					if (currDist < minDst) {
						minDst = currDist;
						minDstN = n;
					}
				}
				
				ind.clear();
				if (!hasGasp) {
					shape.getVertex(minDstN, tmpVec);
					tmpVec.set(fixture.getBody().getWorldPoint(tmpVec));
					boolean correctDirection = Intersector.pointLineSide(
							start, center, tmpVec) < 0;
					for (int n = minDstN; n < shape.getVertexCount(); n++) {
						ind.add(n);
					}
					for (int n = 0; n < minDstN; n++) {
						ind.add(n);
					}
					if (!correctDirection) {
						int z = ind.get(0);
						ind.removeIndex(0);
						ind.reverse();
						ind.insert(0, z);
					}
				} else {
					for (int n = minN - 1; n > -1; n--) {
						ind.add(n);
					}
					for (int n = shape.getVertexCount() - 1; n > maxN ; n--) {
						ind.add(n);
					}
				}
				
				for (int k = 0; k < ind.size; k++) {
					int n = ind.get(k);
					tmpVec.set(tmpVerts.get(n));
					
					float dst = tmpVec.dst(start);
					if (height > data.height) {
						l = dst * data.height / (height - data.height);
						float diff = distance - dst;
						if (l > diff) l = diff;
					} else if (height == 0f) {
						l = distance;
					} else {
						l = distance - dst;
					}
					if (l < 0) l = 0f;
					
					tmpEnd.set(tmpVec).sub(start).limit(l).add(tmpVec);
					
					dynamicSegments[size++] = tmpVec.x;
					dynamicSegments[size++] = tmpVec.y;
					dynamicSegments[size++] = colBits;
					dynamicSegments[size++] = f;
					
					dynamicSegments[size++] = tmpEnd.x;
					dynamicSegments[size++] = tmpEnd.y;
					dynamicSegments[size++] = colBits;
					dynamicSegments[size++] = f;
				}
				
				
				Mesh mesh = new Mesh(
							VertexDataType.VertexArray, staticLight, 2 * ind.size, 0,
							new VertexAttribute(Usage.Position, 2, "vertex_positions"),
							new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
							new VertexAttribute(Usage.Generic, 1, "s"));
				mesh.setVertices(dynamicSegments, 0, size);
				dynamicShadowMeshes.add(mesh);
			} else if (fixtureShape instanceof CircleShape) {
				CircleShape shape = (CircleShape)fixtureShape;
				int size = 0;
				float r = shape.getRadius();
				float dst = tmpVec.set(center).dst(start);
				float a = (float)Math.acos(r/dst) * MathUtils.radDeg;
				if (height > data.height) {
					l = dst * data.height / (height - data.height);
					float diff = distance - dst;
					if (l > diff) l = diff;
				} else if (height == 0f) {
					l = distance;
				} else {
					l = distance - dst;
				}
				if (l < 0) l = 0f;
				
				tmpVec.set(start).sub(center).clamp(r, r).rotate(a);
				tmpStart.set(center).add(tmpVec);
				dynamicSegments[size++] = tmpStart.x;
				dynamicSegments[size++] = tmpStart.y;
				dynamicSegments[size++] = colBits;
				dynamicSegments[size++] = f;
				
				tmpEnd.set(tmpStart).sub(start).limit(l).add(tmpStart);
				dynamicSegments[size++] = tmpEnd.x;
				dynamicSegments[size++] = tmpEnd.y;
				dynamicSegments[size++] = colBits;
				dynamicSegments[size++] = f;
				
				tmpVec.rotate(-2f*a);
				tmpStart.set(center).add(tmpVec);
				dynamicSegments[size++] = tmpStart.x;
				dynamicSegments[size++] = tmpStart.y;
				dynamicSegments[size++] = colBits;
				dynamicSegments[size++] = f;
				
				tmpEnd.set(tmpStart).sub(start).limit(l).add(tmpStart);
				dynamicSegments[size++] = tmpEnd.x;
				dynamicSegments[size++] = tmpEnd.y;
				dynamicSegments[size++] = colBits;
				dynamicSegments[size++] = f;
				
				Mesh mesh = new Mesh(
						VertexDataType.VertexArray, staticLight, size / 4, 0,
						new VertexAttribute(Usage.Position, 2, "vertex_positions"),
						new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
						new VertexAttribute(Usage.Generic, 1, "s"));
				mesh.setVertices(dynamicSegments, 0, size);
				dynamicShadowMeshes.add(mesh);
			}
		}
	}
	
	/**
	 * Sets light distance
	 * 
	 * <p>MIN value capped to 0.1f meter
	 * <p>Actual recalculations will be done only on {@link #update()} call
	 */
	@Override
	public void setDistance(float dist) {
		dist *= RayHandler.gammaCorrectionParameter;
		this.distance = dist < 0.01f ? 0.01f : dist;
		dirty = true;
	}
	
	public void setHeight(float height) {
		this.height = height;
	}
	
	/** Updates light basing on it's distance and rayNum **/
	void setEndPoints() {
		float angleNum = 360f / (rayNum - 1);
		for (int i = 0; i < rayNum; i++) {
			final float angle = angleNum * i;
			sin[i] = MathUtils.sinDeg(angle);
			cos[i] = MathUtils.cosDeg(angle);
			endX[i] = distance * cos[i];
			endY[i] = distance * sin[i];
		}
	}
	
	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void setDirection(float directionDegree) {
	}

}
