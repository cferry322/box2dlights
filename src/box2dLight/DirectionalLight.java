
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
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.QueryCallback;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.Shape.Type;

/**
 * Light which source is at infinite distance
 * 
 * <p>Extends {@link Light}
 * 
 * @author kalle_h
 */
public class DirectionalLight extends Light {

	protected final Vector2 start[];
	protected final Vector2 end[];
	protected float sin;
	protected float cos;
	
	/** Dynamic shadows variables **/
	protected final Vector2 lstart = new Vector2();
	protected float xDisp;
	protected float yDisp;

	/**
	 * Creates directional light which source is at infinite distance,
	 * direction and intensity is same everywhere
	 * 
	 * <p>-90 direction is straight from up
	 * 
	 * @param rayHandler
	 *            not {@code null} instance of RayHandler
	 * @param rays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 * @param color
	 *            color, set to {@code null} to use the default color
	 * @param directionDegree
	 *            direction in degrees
	 */
	public DirectionalLight(RayHandler rayHandler, int rays, Color color,
			float directionDegree) {
		
		super(rayHandler, rays, color, Float.POSITIVE_INFINITY, directionDegree);
		
		vertexNum = (vertexNum - 1) * 2;
		start = new Vector2[rayNum];
		end = new Vector2[rayNum];
		for (int i = 0; i < rayNum; i++) {
			start[i] = new Vector2();
			end[i] = new Vector2();
		}
		
		lightMesh = new Mesh(
				VertexDataType.VertexArray, staticLight, vertexNum, 0,
				new VertexAttribute(Usage.Position, 2, "vertex_positions"),
				new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
				new VertexAttribute(Usage.Generic, 1, "s"));
		softShadowMesh = new Mesh(
				VertexDataType.VertexArray, staticLight, vertexNum, 0,
				new VertexAttribute(Usage.Position, 2, "vertex_positions"),
				new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
				new VertexAttribute(Usage.Generic, 1, "s"));
		
		update();
	}

	@Override
	public void setDirection (float direction) {
		this.direction = direction;
		sin = MathUtils.sinDeg(direction);
		cos = MathUtils.cosDeg(direction);
		if (staticLight) dirty = true;
	}
	
	@Override
	void update () {
		if (rayHandler.pseudo3d && height != -1f) {
			float width = (rayHandler.x2 - rayHandler.x1);
			float height = (rayHandler.y2 - rayHandler.y1);
			float sizeOfScreen = width > height ? width : height;
			xDisp = -sizeOfScreen * cos;
			yDisp = -sizeOfScreen * sin;
			
			prepeareFixtureData();
		}
		
		if (staticLight && !dirty) return;
		dirty = false;

		final float width = (rayHandler.x2 - rayHandler.x1);
		final float height = (rayHandler.y2 - rayHandler.y1);
		final float sizeOfScreen = width > height ? width : height;

		float xAxelOffSet = sizeOfScreen * cos;
		float yAxelOffSet = sizeOfScreen * sin;

		// preventing length <0 assertion error on box2d.
		if ((xAxelOffSet * xAxelOffSet < 0.1f) && (yAxelOffSet * yAxelOffSet < 0.1f)) {
			xAxelOffSet = 1;
			yAxelOffSet = 1;
		}
		
		final float widthOffSet = sizeOfScreen * -sin;
		final float heightOffSet = sizeOfScreen * cos;

		float x = (rayHandler.x1 + rayHandler.x2) * 0.5f - widthOffSet;
		float y = (rayHandler.y1 + rayHandler.y2) * 0.5f - heightOffSet;

		final float portionX = 2f * widthOffSet / (rayNum - 1);
		x = (MathUtils.floor(x / (portionX * 2))) * portionX * 2;
		final float portionY = 2f * heightOffSet / (rayNum - 1);
		y = (MathUtils.ceil(y / (portionY * 2))) * portionY * 2;
		for (int i = 0; i < rayNum; i++) {
			final float steppedX = i * portionX + x;
			final float steppedY = i * portionY + y;
			m_index = i;
			start[i].x = steppedX - xAxelOffSet;
			start[i].y = steppedY - yAxelOffSet;

			mx[i] = end[i].x = steppedX + xAxelOffSet;
			my[i] = end[i].y = steppedY + yAxelOffSet;

			if (rayHandler.world != null && !xray && !rayHandler.pseudo3d) {
				rayHandler.world.rayCast(ray, start[i], end[i]);
			}
		}

		// update light mesh
		// ray starting point
		int size = 0;
		final int arraySize = rayNum;

		for (int i = 0; i < arraySize; i++) {
			segments[size++] = start[i].x;
			segments[size++] = start[i].y;
			segments[size++] = colorF;
			segments[size++] = 1f;
			segments[size++] = mx[i];
			segments[size++] = my[i];
			segments[size++] = colorF;
			segments[size++] = 1f;
		}
		lightMesh.setVertices(segments, 0, size);

		if (!soft || xray || rayHandler.pseudo3d) return;

		size = 0;
		for (int i = 0; i < arraySize; i++) {
			segments[size++] = mx[i];
			segments[size++] = my[i];
			segments[size++] = colorF;
			segments[size++] = 1f;

			segments[size++] = mx[i] + softShadowLength * cos;
			segments[size++] = my[i] + softShadowLength * sin;
			segments[size++] = zeroColorBits;
			segments[size++] = 1f;
		}
		softShadowMesh.setVertices(segments, 0, size);
	}

	@Override
	void render () {
		rayHandler.lightRenderedLastFrame++;

		lightMesh.render(
				rayHandler.lightShader, GL20.GL_TRIANGLE_STRIP, 0, vertexNum);
		
		if (soft && !xray && !rayHandler.pseudo3d) {
			softShadowMesh.render(
				rayHandler.lightShader, GL20.GL_TRIANGLE_STRIP, 0, vertexNum);
		}
	}
	
	@Override
	void dynamicShadowRender () {
		if (height == -1f) return;
		
		updateDynamicShadowMeshes();
		for (Mesh m : dynamicShadowMeshes) {
			m.render(rayHandler.lightShader, GL20.GL_TRIANGLE_STRIP);
		}
	}
	
	protected void prepeareFixtureData() {
		affectedFixtures.clear();
		rayHandler.world.QueryAABB(
				dynamicShadowCallback,
				rayHandler.x1, rayHandler.y1,
				rayHandler.x2, rayHandler.y2);
		for (Fixture fixture : affectedFixtures) {
			if (fixture.getUserData() instanceof LightData) {
				LightData data = (LightData)fixture.getUserData();
				data.shadowsDropped++;
			}
		}
	}
	
	protected void updateDynamicShadowMeshes() {
		for (Mesh mesh : dynamicShadowMeshes) {
			mesh.dispose();
		}
		dynamicShadowMeshes.clear();
		
		float colBits = rayHandler.ambientLight.toFloatBits();
		for (Fixture fixture : affectedFixtures) {
			LightData data = (LightData)fixture.getUserData();
			if (data == null || fixture.isSensor()) continue;
			
			Shape fixtureShape = fixture.getShape();
			Type type = fixtureShape.getType();
			center.set(fixture.getBody().getWorldCenter());
			lstart.set(center).add(xDisp, yDisp);
			
			int size = 0;
			float l = data.height /
					(float)Math.tan(height * MathUtils.degRad);
			float f = 1f / data.shadowsDropped;
			if (type == Type.Polygon || type == Type.Chain) {
				boolean isPolygon = (type == Type.Polygon);
				ChainShape cShape = isPolygon ?
						null : (ChainShape)fixtureShape;
				PolygonShape pShape = isPolygon ?
						(PolygonShape)fixtureShape : null;
				int vertexCount = isPolygon ?
						pShape.getVertexCount() :
							cShape.getVertexCount();
				int minN = -1;
				int maxN = -1;
				int minDstN = -1;
				float minDst = Float.POSITIVE_INFINITY;
				boolean hasGasp = false;
				tmpVerts.clear();
				for (int n = 0; n < vertexCount; n++) {
					if (isPolygon) {
						pShape.getVertex(n, tmpVec);
					} else {
						cShape.getVertex(n, tmpVec);
					}
					tmpVec.set(fixture.getBody().getWorldPoint(tmpVec));
					tmpVerts.add(tmpVec.cpy());
					tmpEnd.set(tmpVec).sub(lstart).limit(0.01f).add(tmpVec);
					if (fixture.testPoint(tmpEnd)) {
						if (n > minN) minN = n;
						maxN = n;
						hasGasp = true;
						continue;
					}
					float currDist = tmpVec.dst2(lstart);
					if (currDist < minDst) {
						minDst = currDist;
						minDstN = n;
					}
				}
				
				ind.clear();
				if (!hasGasp) {
					tmpVec.set(tmpVerts.get(minDstN));
					boolean correctDirection = Intersector.pointLineSide(
							lstart, center, tmpVec) < 0;
					for (int n = minDstN; n < vertexCount; n++) {
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
					for (int n = vertexCount - 1; n > maxN ; n--) {
						ind.add(n);
					}
				}
				
				for (int k = 0; k < ind.size; k++) {
					int n = ind.get(k);
					tmpVec.set(tmpVerts.get(n));
					tmpEnd.set(tmpVec).sub(lstart).limit(l).add(tmpVec);
					
					segments[size++] = tmpVec.x;
					segments[size++] = tmpVec.y;
					segments[size++] = zeroColorBits;
					segments[size++] = f;
					
					segments[size++] = tmpEnd.x;
					segments[size++] = tmpEnd.y;
					segments[size++] = colBits;
					segments[size++] = f;
				}
			} else if (type == Type.Circle) {
				CircleShape shape = (CircleShape)fixtureShape;
				
				float r = shape.getRadius();
				float dst = tmpVec.set(center).dst(lstart);
				float a = (float) Math.acos(r/dst);
				
				tmpVec.set(lstart).sub(center).clamp(r, r).rotateRad(a);
				tmpStart.set(center).add(tmpVec);
				
				float angle = (MathUtils.PI2 - 2f * a) /
						RayHandler.CIRCLE_APPROX_POINTS;
				for (int k = 0; k < RayHandler.CIRCLE_APPROX_POINTS; k++) {
					tmpStart.set(center).add(tmpVec);
					segments[size++] = tmpStart.x;
					segments[size++] = tmpStart.y;
					segments[size++] = zeroColorBits;
					segments[size++] = f;
					
					tmpEnd.set(tmpStart).sub(lstart).limit(l).add(tmpStart);
					segments[size++] = tmpEnd.x;
					segments[size++] = tmpEnd.y;
					segments[size++] = colBits;
					segments[size++] = f;
					
					tmpVec.rotateRad(angle);
				}
			} else if (type == Type.Edge) {
				EdgeShape shape = (EdgeShape)fixtureShape;
				
				shape.getVertex1(tmpVec);
				tmpVec.set(fixture.getBody().getWorldPoint(tmpVec));
				
				segments[size++] = tmpVec.x;
				segments[size++] = tmpVec.y;
				segments[size++] = zeroColorBits;
				segments[size++] = f;
				
				tmpEnd.set(tmpVec).sub(lstart).limit(l).add(tmpVec);
				segments[size++] = tmpEnd.x;
				segments[size++] = tmpEnd.y;
				segments[size++] = colBits;
				segments[size++] = f;
				
				shape.getVertex2(tmpVec);
				tmpVec.set(fixture.getBody().getWorldPoint(tmpVec));
				segments[size++] = tmpVec.x;
				segments[size++] = tmpVec.y;
				segments[size++] = zeroColorBits;
				segments[size++] = f;
				
				tmpEnd.set(tmpVec).sub(lstart).limit(l).add(tmpVec);
				segments[size++] = tmpEnd.x;
				segments[size++] = tmpEnd.y;
				segments[size++] = colBits;
				segments[size++] = f;
			}		
			
			Mesh mesh = new Mesh(
					VertexDataType.VertexArray, staticLight, size / 4, 0,
					new VertexAttribute(Usage.Position, 2, "vertex_positions"),
					new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
					new VertexAttribute(Usage.Generic, 1, "s"));
			mesh.setVertices(segments, 0, size);
			dynamicShadowMeshes.add(mesh);
		}
	}
	
	@Override
	public boolean contains (float x, float y) {
		boolean oddNodes = false;
		float x2 = mx[rayNum] = start[0].x;
		float y2 = my[rayNum] = start[0].y;
		float x1, y1;
		for (int i = 0; i <= rayNum; x2 = x1, y2 = y1, ++i) {
			x1 = mx[i];
			y1 = my[i];
			if (((y1 < y) && (y2 >= y)) || (y1 >= y) && (y2 < y)) {
				if ((y - y1) / (y2 - y1) * (x2 - x1) < (x - x1)) oddNodes = !oddNodes;
			}
		}
		for (int i = 0; i < rayNum; x2 = x1, y2 = y1, ++i) {
			x1 = start[i].x;
			y1 = start[i].y;
			if (((y1 < y) && (y2 >= y)) || (y1 >= y) && (y2 < y)) {
				if ((y - y1) / (y2 - y1) * (x2 - x1) < (x - x1)) oddNodes = !oddNodes;
			}
		}
		return oddNodes;
	}
	
	final QueryCallback dynamicShadowCallback = new QueryCallback() {

		@Override
		public boolean reportFixture(Fixture fixture) {
			if (fixture.getUserData() instanceof LightData) {
				LightData data = (LightData)fixture.getUserData();
				data.shadowsDropped = 0;
			}
			affectedFixtures.add(fixture);
			return true;
		}
		
	};
	
	/** Sets the horizontal angle for directional light in degrees
	 * 
	 * <p> This could be used to simulate sun cycles **/
	@Override
	public void setHeight(float degrees) {
		if (degrees < 0f) height = 0f;
		else {
			degrees = degrees % 360;
			if (degrees > 180f) {
				height = -1f;
			}
			else if (degrees > 90f) height = degrees - 90f;
			else height = degrees;
		}
	}

	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void attachToBody (Body body) {
	}
	
	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void setPosition (float x, float y) {
	}

	/** Not applicable for this light type
	 * <p>Always return {@code null}
	 **/
	@Deprecated
	@Override
	public Body getBody () {
		return null;
	}

	/** Not applicable for this light type
	 * <p>Always return {@code 0}
	 **/
	@Deprecated
	@Override
	public float getX () {
		return 0;
	}

	/** Not applicable for this light type
	 * <p>Always return {@code 0}
	 **/
	@Deprecated
	@Override
	public float getY () {
		return 0;
	}

	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void setPosition (Vector2 position) {
	}

	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void setDistance(float dist) {
	}
	
	/** Not applicable for this light type **/
	@Deprecated
	@Override
	public void setIgnoreAttachedBody(boolean flag) {
	}
	
	/** Not applicable for this light type
	 * <p>Always return {@code false}
	 **/
	@Deprecated
	@Override
	public boolean getIgnoreAttachedBody() {
		return false;
	}


}
